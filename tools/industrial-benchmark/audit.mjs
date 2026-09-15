import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export function amount(value, allowZero = false) {
  if (typeof value !== 'string' || !/^(0|[1-9][0-9]*)$/.test(value)) {
    throw new Error(`Noncanonical exact count: ${value}`);
  }
  const number = BigInt(value);
  if (!allowZero && number === 0n) throw new Error('Zero count');
  return value;
}

export function verifyChunk(summary, chunk, index) {
  if (summary.state !== 'COMPLETE' || !summary.captureId) throw new Error('Capture did not complete');
  if (chunk.captureId !== summary.captureId || chunk.chunkIndex !== String(index)) {
    throw new Error('Mixed capture generations or misplaced chunk');
  }
  if (!Array.isArray(chunk.recipes) || chunk.recipes.length > 1000) throw new Error('Invalid chunk');
}

function stack(value, kind) {
  if (!value || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(value.id)) throw new Error('Invalid stack ID');
  if (value.nbt !== null && typeof value.nbt !== 'string') throw new Error('Missing NBT identity');
  return { kind, id: value.id, nbt: value.nbt, amount: amount(value.amount) };
}

export function normalize(row) {
  const result = { id: row.id, serializer: row.serializer, inputs: [], outputs: [],
    catalysts: [], unsupported: [], source: row.jsonSource };
  if (row.error) result.unsupported.push(`capture: ${row.error}`);
  if (row.jsonSource === 'runtime-codec') {
    if (!row.resolved?.inputs || !row.resolved?.outputs) throw new Error(`Incomplete GT capture: ${row.id}`);
    for (const direction of ['inputs', 'outputs']) {
      for (const [kind, contents] of Object.entries(row.resolved[direction])) {
        if (!['item', 'fluid'].includes(kind)) {
          result.unsupported.push(`unhandled ${direction} capability: ${kind}`);
          continue;
        }
        for (const content of contents) {
          const chance = BigInt(amount(content.chance, true));
          const maxChance = BigInt(amount(content.maxChance));
          const alternatives = (content.alternatives ?? []).map(value => stack(value, kind));
          if (!alternatives.length) result.unsupported.push(`empty ${direction} ingredient`);
          if (chance !== 0n && chance !== maxChance) result.unsupported.push(`probabilistic ${direction}`);
          if (direction === 'inputs') {
            (chance === 0n ? result.catalysts : result.inputs).push({ alternatives,
              runtimeClass: content.runtimeClass });
          } else {
            if (alternatives.length !== 1 || chance !== maxChance) result.unsupported.push('non-exact output');
            result.outputs.push(...alternatives);
          }
        }
      }
    }
    for (const direction of ['tickInputs', 'tickOutputs']) {
      for (const [kind, contents] of Object.entries(row.resolved[direction] ?? {})) {
        if (kind !== 'eu' && contents.length) result.unsupported.push(`tick ${kind}`);
      }
    }
    if (row.json?.ingredientActions?.length || row.json?.ingredient_actions?.length) {
      result.unsupported.push('ingredient actions');
    }
    // Machine conditions and catalysts are not ME-consumed ingredients. They remain
    // explicit preconditions; no fixture may silently assume the machines exist.
    result.machine = { conditions: row.json?.recipeConditions ?? row.json?.conditions ?? [],
      tickInputs: row.json?.tickInputs ?? row.json?.tick_inputs ?? {},
      duration: row.json?.duration ?? null };
  } else if (['minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'minecraft:smelting',
    'minecraft:blasting', 'minecraft:smoking', 'minecraft:campfire_cooking'].includes(row.serializer)) {
    if (!row.resolved || row.resolved.special) result.unsupported.push('dynamic or unavailable vanilla recipe');
    for (const slot of row.resolved?.inputs ?? []) {
      if (!slot.alternatives.length) {
        // A vacant grid slot and a tag matching nothing are not yet distinguished.
        result.unsupported.push('empty or unresolved vanilla ingredient');
        continue;
      }
      result.inputs.push({ alternatives: slot.alternatives.map(value => stack(value, 'item')),
        runtimeClass: slot.runtimeClass });
      if (slot.runtimeClass !== 'net.minecraft.world.item.crafting.Ingredient') {
        result.unsupported.push(`custom ingredient: ${slot.runtimeClass}`);
      }
    }
    const output = row.resolved?.output;
    if (!output || output.amount === '0') result.unsupported.push('missing vanilla output');
    else result.outputs.push(stack(output, 'item'));
  } else {
    result.unsupported.push(`serializer: ${row.serializer}`);
    // Index provenance-only outputs for the coverage report, never as usable recipes.
    const found = [];
    function visit(value) {
      if (Array.isArray(value)) return value.forEach(visit);
      if (!value || typeof value !== 'object') return;
      for (const kind of ['item', 'fluid', 'gas', 'infuse_type', 'slurry', 'pigment']) {
        if (typeof value[kind] === 'string') found.push({ kind, id: value[kind], nbt: null, amount: null });
      }
    }
    for (const name of ['output', 'outputs', 'result', 'results']) visit(row.json?.[name]);
    if (row.resolved?.output?.amount !== '0' && row.resolved?.output?.id) {
      found.push(stack(row.resolved.output, 'item'));
    }
    result.outputs.push(...found);
  }
  result.unsupported = [...new Set(result.unsupported)];
  return result;
}

export async function audit(directory, root = 'evolvedmekanism:creative_control_circuit') {
  const summary = JSON.parse(await readFile(join(directory, 'summary.json'), 'utf8'));
  if (summary.state !== 'COMPLETE' || !summary.captureId) throw new Error('Capture did not complete');
  if (!Number.isSafeInteger(summary.chunks) || summary.chunks <= 0) throw new Error('Incomplete capture manifest');
  const recipes = new Map();
  const sources = [];
  const errors = [];
  for (let i = 0; i < summary.chunks; i++) {
    const name = `chunk-${i}.json`;
    const bytes = await readFile(join(directory, name));
    sources.push({ file: name, sha256: createHash('sha256').update(bytes).digest('hex') });
    const chunk = JSON.parse(bytes);
    verifyChunk(summary, chunk, i);
    for (const row of chunk.recipes) {
      if (recipes.has(row.id)) throw new Error(`Duplicate final recipe ID: ${row.id}`);
      try { recipes.set(row.id, normalize(row)); }
      catch (error) { recipes.set(row.id, { id: row.id, inputs: [], outputs: [], unsupported: [error.message] });
        errors.push({ id: row.id, error: error.message }); }
    }
  }
  if (recipes.size !== summary.recipes) throw new Error('Recipe count does not match completed capture');
  const producers = new Map();
  for (const recipe of recipes.values()) {
    for (const output of recipe.outputs) {
      const key = output.kind + ':' + output.id;
      if (!producers.has(key)) producers.set(key, []);
      if (!producers.get(key).includes(recipe.id)) producers.get(key).push(recipe.id);
    }
  }
  const rootKey = `item:${root}`;
  if (!producers.has(rootKey)) throw new Error(`No final recipe found for ${root}`);
  const pending = [rootKey];
  const keys = new Set();
  const reachable = new Set();
  const alternatives = [];
  const frontier = [];
  const unsupported = [];
  while (pending.length) {
    const key = pending.pop();
    if (keys.has(key)) continue;
    keys.add(key);
    const ids = producers.get(key) ?? [];
    if (!ids.length) frontier.push(key);
    if (ids.length > 1) alternatives.push({ key, producers: ids });
    for (const id of ids) {
      if (reachable.has(id)) continue;
      reachable.add(id);
      const recipe = recipes.get(id);
      if (recipe.unsupported.length) unsupported.push({ id, reasons: recipe.unsupported });
      for (const input of recipe.inputs) {
        for (const alternative of input.alternatives) pending.push(alternative.kind + ':' + alternative.id);
      }
    }
  }
  const report = { schema: 1, status: 'COVERAGE_ONLY_NOT_ACCEPTANCE', captureId: summary.captureId,
    root, captureMillis: summary.captureMillis,
    recipes: recipes.size, reachableRecipes: reachable.size, reachableKeys: keys.size,
    rootRecipes: producers.get(rootKey).map(id => recipes.get(id)),
    alternatives, frontier, unsupported, captureErrors: summary.errors, normalizationErrors: errors, sources,
    warnings: [
      'Coverage keys ignore NBT variants to find all producers; do not use this index as a planner.',
      'Frontier entries are unresolved, NOT automatically declared raw resources.',
      'Unsupported branches are not expanded or treated as successful plans.',
      'No ME provider order or inventory is inferred from RecipeManager.'
    ] };
  await writeFile(join(directory, 'normalized-recipes.json'), JSON.stringify([...recipes.values()]));
  await writeFile(join(directory, 'coverage-report.json'), JSON.stringify(report, null, 2));
  return report;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const report = await audit(resolve(process.argv[2]), process.argv[3]);
  console.log(JSON.stringify({ recipes: report.recipes, reachable: report.reachableRecipes,
    rootRecipes: report.rootRecipes.map(r => r.id), alternativeKeys: report.alternatives.length,
    unsupported: report.unsupported.length, frontier: report.frontier.length,
    captureErrors: report.captureErrors.length, normalizationErrors: report.normalizationErrors.length }));
}
