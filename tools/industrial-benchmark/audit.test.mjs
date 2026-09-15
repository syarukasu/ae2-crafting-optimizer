import test from 'node:test';
import assert from 'node:assert/strict';
import { amount, normalize, verifyChunk } from './audit.mjs';

const stack = { id: 'minecraft:iron_ingot', amount: '16', nbt: '{Damage:1}' };
const content = { chance: '10000', maxChance: '10000', alternatives: [stack], runtimeClass: 'SizedIngredient' };
const row = { id: 'test:machine', serializer: 'gtceu:machine', jsonSource: 'runtime-codec',
  resolved: { inputs: { item: [content] }, outputs: { item: [content] } } };

test('never round exact integers through JavaScript numbers', () => {
  assert.equal(amount('9223372036854775808'), '9223372036854775808');
  for (const value of [1, 9223372036854775808, '1e64', '-1', '01', '1.0', '0']) {
    assert.throws(() => amount(value));
  }
});
test('preserve NBT, count and all ingredient alternatives', () => {
  const copy = structuredClone(row);
  copy.resolved.inputs.item[0].alternatives.push({ ...stack, nbt: '{Damage:2}' });
  const normalized = normalize(copy);
  assert.equal(normalized.inputs[0].alternatives.length, 2);
  assert.equal(normalized.inputs[0].alternatives[0].nbt, '{Damage:1}');
  assert.equal(normalized.outputs[0].amount, '16');
});
test('nonconsumable catalysts stay separate from per-craft consumption', () => {
  const copy = structuredClone(row);
  copy.resolved.inputs.item[0].chance = '0';
  const normalized = normalize(copy);
  assert.equal(normalized.inputs.length, 0);
  assert.equal(normalized.catalysts.length, 1);
});
test('chance products cannot silently become deterministic outputs', () => {
  const copy = structuredClone(row);
  copy.resolved.outputs.item[0].chance = '5000';
  assert.ok(normalize(copy).unsupported.includes('non-exact output'));
});
test('unsupported serializers are indexed for coverage, never accepted', () => {
  const normalized = normalize({ id: 'test:dynamic', serializer: 'example:dynamic',
    json: { output: { item: 'minecraft:diamond', count: 5 } } });
  assert.ok(normalized.unsupported.length);
  assert.equal(normalized.outputs[0].amount, null);
});
test('empty GT ingredient is a failed coverage boundary', () => {
  const copy = structuredClone(row);
  copy.resolved.inputs.item[0].alternatives = [];
  assert.ok(normalize(copy).unsupported.includes('empty inputs ingredient'));
});
test('preserve actual GT recipeConditions including sterile cleanroom', () => {
  const copy = structuredClone(row);
  copy.json = { recipeConditions: [{ type: 'cleanroom', cleanroom: 'sterile_cleanroom' }] };
  assert.deepEqual(normalize(copy).machine.conditions, copy.json.recipeConditions);
});
test('a partial new capture cannot reuse an old completed summary or chunks', () => {
  const summary = { state: 'COMPLETE', captureId: 'new' };
  assert.throws(() => verifyChunk({ ...summary, state: 'IN_PROGRESS' }, {}, 0));
  assert.throws(() => verifyChunk(summary, { captureId: 'old', chunkIndex: '0', recipes: [] }, 0));
  assert.throws(() => verifyChunk(summary, { captureId: 'new', chunkIndex: '1', recipes: [] }, 0));
  assert.doesNotThrow(() => verifyChunk(summary, { captureId: 'new', chunkIndex: '0', recipes: [] }, 0));
});

test('an unresolved vanilla tag must not become a recipe with no input', () => {
  const normalized = normalize({ id: 'test:empty_tag', serializer: 'minecraft:smelting',
    resolved: { special: false, inputs: [{ alternatives: [],
      runtimeClass: 'net.minecraft.world.item.crafting.Ingredient' }], output: stack } });
  assert.ok(normalized.unsupported.includes('empty or unresolved vanilla ingredient'));
});

test('a missing vanilla runtime result stays an explicit unsupported boundary', () => {
  const normalized = normalize({ id: 'test:missing_result', serializer: 'minecraft:crafting_shaped' });
  assert.ok(normalized.unsupported.includes('missing vanilla output'));
  assert.equal(normalized.outputs.length, 0);
});
