// priority: -2147483647
// Install only in a private test server. Never register or change recipes here.
var aco190RecipeReferences = [];
var aco190CaptureFinished = false;
var aco190CapturePending = false;
ServerEvents.recipes(event => {
  aco190RecipeReferences = [];
  var original = event.originalRecipes.values().iterator();
  while (original.hasNext()) aco190RecipeReferences.push(original.next());
  var added = event.addedRecipes.iterator();
  while (added.hasNext()) aco190RecipeReferences.push(added.next());
});

function aco190Capture(event) {
  var permission = JsonIO.read('kubejs/aco_capture_enabled.json');
  if (!permission || permission.enabled !== true) return;
  var captureId = String(Java.loadClass('java.util.UUID').randomUUID());
  JsonIO.write('industrial-capture/summary.json', {schema: 1, state: 'IN_PROGRESS', captureId: captureId});
  var GsonBuilder = Java.loadClass('com.google.gson.GsonBuilder');
  var JsonObject = Java.loadClass('com.google.gson.JsonObject');
  var JsonArray = Java.loadClass('com.google.gson.JsonArray');
  var JsonParser = Java.loadClass('com.google.gson.JsonParser');
  var HashMap = Java.loadClass('java.util.HashMap');
  var Registries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
  var GTSerializer = Java.loadClass('com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer');
  var JsonOps = Java.loadClass('com.mojang.serialization.JsonOps');
  var RegistryOps = Java.loadClass('net.minecraft.resources.RegistryOps');
  var registryOps = RegistryOps.create(JsonOps.INSTANCE, event.server.registryAccess());
  var gson = new GsonBuilder().disableHtmlEscaping().create();
  var chunk = new JsonArray();
  var chunkNumber = 0;
  var started = Date.now();
  var summary = {schema: 1, state: 'COMPLETE', captureId: captureId,
    phase: 'RecipeManager read after server startup on server thread', minecraft: '1.20.1',
    recipes: 0, gt: 0, json: 0, errors: [], limitations: [
      'Not an encoded ME pattern snapshot; producer ordering is not implied.',
      'KubeJS JSON is provenance, not proof of dynamic recipe behavior.',
      'No production world, inventory or recipes are changed.'
    ]};
  function stack(value, fluid) {
    var tag = String(fluid ? value.getTag() : value.getNbtString());
    return {id: String(fluid ? Registries.FLUID.getKey(value.getFluid()) : Registries.ITEM.getKey(value.getItem())),
      amount: String(fluid ? value.getAmount() : value.getCount()),
      nbt: tag === 'null' ? null : tag};
  }
  function flush() {
    if (chunk.size() === 0) return;
    var data = new JsonObject();
    data.addProperty('captureId', captureId);
    data.addProperty('chunkIndex', String(chunkNumber));
    data.add('recipes', chunk);
    JsonIO.write('industrial-capture/chunk-' + chunkNumber + '.json', data);
    chunkNumber++;
    chunk = new JsonArray();
  }
  function gtContents(map) {
    var result = {};
    var entries = map.entrySet().iterator();
    while (entries.hasNext()) {
      var entry = entries.next();
      var name = String(entry.getKey().name);
      var list = [];
      var it = entry.getValue().iterator();
      while (it.hasNext()) {
        var c = it.next();
        var content = c.content;
        var row = {chance: String(c.chance), maxChance: String(c.maxChance),
          tierChanceBoost: String(c.tierChanceBoost), runtimeClass: String(content.getClass().getName())};
        if (name === 'item' || name === 'fluid') {
          var items = name === 'fluid' ? content.getStacks() : content.getStacks().toArray();
          row.alternatives = [];
          for (var k = 0; k < items.length; k++) row.alternatives.push(stack(items[k], name === 'fluid'));
        }
        list.push(row);
      }
      result[name] = list;
    }
    return result;
  }
  {
    var manager = event.server.getRecipeManager();
    var recipes = manager.getRecipes().iterator();
    var jsById = new HashMap();
    for (var ref = 0; ref < aco190RecipeReferences.length; ref++) {
      var r = aco190RecipeReferences[ref];
      if (!r.removed) jsById.put(String(r.getId()), r);
    }
    while (recipes.hasNext()) {
      var recipe = recipes.next();
      var id = String(recipe.getId());
      var row = new JsonObject();
      row.addProperty('id', id);
      row.addProperty('runtimeClass', String(recipe.getClass().getName()));
      row.addProperty('serializer', String(Registries.RECIPE_SERIALIZER.getKey(recipe.getSerializer())));
      try {
        if (String(recipe.getClass().getName()) === 'com.gregtechceu.gtceu.api.recipe.GTRecipe') {
          var encoded = GTSerializer.CODEC.encodeStart(registryOps, recipe);
          if (!encoded.result().isPresent()) throw new Error(String(encoded.error()));
          row.add('json', encoded.result().get());
          row.addProperty('jsonSource', 'runtime-codec');
          row.add('resolved', JsonParser.parseString(JSON.stringify({
            inputs: gtContents(recipe.inputs), outputs: gtContents(recipe.outputs),
            tickInputs: gtContents(recipe.tickInputs), tickOutputs: gtContents(recipe.tickOutputs)
          })));
          summary.gt++;
        } else {
          var jsRecipe = jsById.get(id);
          if (jsRecipe != null && jsRecipe.json != null) {
            row.add('json', jsRecipe.json.deepCopy());
            row.addProperty('jsonSource', 'kubejs-provenance');
            summary.json++;
          } else row.addProperty('jsonSource', 'unavailable');
          var resolved = {inputs: [], output: null, special: recipe.isSpecial()};
          if (!resolved.special) {
            var ingredients = recipe.getIngredients().iterator();
            while (ingredients.hasNext()) {
              var ingredient = ingredients.next();
              var alternatives = [];
              var items = ingredient.getStacks().toArray();
              for (var j = 0; j < items.length; j++) alternatives.push(stack(items[j], false));
              resolved.inputs.push({runtimeClass: String(ingredient.getClass().getName()), alternatives: alternatives});
            }
            resolved.output = stack(recipe.getResultItem(event.server.registryAccess()), false);
          } else {
            resolved.output = {id: 'minecraft:air', amount: '0', nbt: null};
          }
          row.add('resolved', JsonParser.parseString(JSON.stringify(resolved)));
        }
      } catch (error) {
        row.addProperty('error', String(error));
        summary.errors.push({id: id, error: String(error)});
      }
      chunk['add(com.google.gson.JsonElement)'](row);
      summary.recipes++;
      if (chunk.size() >= 1000) flush();
    }
  }
  flush();
  summary.chunks = chunkNumber;
  summary.captureMillis = Date.now() - started;
  summary.ingredientAlternativesOrder = 'KubeJS ItemStackSet enumeration, not ME candidate priority';
  JsonIO.write('industrial-capture/summary.json', summary);
  console.info('[ACO-INDUSTRIAL-CAPTURE] ' + summary.recipes + ' recipes; errors=' + summary.errors.length);
  aco190CaptureFinished = summary.errors.length === 0;
}

ServerEvents.loaded(event => { aco190CapturePending = true; });
ServerEvents.customCommand('aco_industrial_capture', aco190Capture);

ServerEvents.tick(event => {
  if (aco190CaptureFinished) {
    aco190CaptureFinished = false;
    event.server.halt(false);
  } else if (aco190CapturePending) {
    aco190CapturePending = false;
    aco190Capture(event);
  }
});
