"use client";

import { Clock } from "lucide-react";
import { C, FormatGuide } from "@/features/admin/format-guide";

const IMAGE_HOSTS = "res.cloudinary.com, images.unsplash.com, images.pexels.com, upload.wikimedia.org";

const RECIPE_CSV_EXAMPLE = `title,prep_time_mins,cook_time_mins,difficulty_level,estimated_cost_bdt,base_servings,tags_pipe,ingredients_csv,instructions_pipe
Egg Bhurji Toast,5,8,EASY,70.00,2,Breakfast|Egg,Egg:3:pcs|Onion:1:pcs|Bread:4:slice|Salt:0.5:tsp|Coriander Leaves:1:tbsp:optional,Chop the onion|Fry the onion for 2 minutes|Add the eggs and stir until set|Serve on toast
"Chira Poha, Bangladeshi style",5,10,EASY,50.00,2,Breakfast|Bangladeshi,Flattened Rice (Chira):2:cup|Potato:1:pcs|Peanuts:2:tbsp:optional,Rinse the chira|Fry the potato until soft|Mix in the chira for 3 minutes`;

const RECIPE_JSON_EXAMPLE = `[
  {
    "title": "Egg Bhurji Toast",
    "description": "Spicy scrambled eggs on crispy toast",
    "prep_time_mins": 5,
    "cook_time_mins": 8,
    "difficulty_level": "EASY",
    "estimated_cost_bdt": 70.00,
    "base_servings": 2,
    "tags": ["Breakfast", "Egg"],
    "ingredients": [
      { "name": "Egg", "quantity": 3, "unit": "pcs" },
      { "name": "Bread", "quantity": 4, "unit": "slice" },
      { "name": "Coriander Leaves", "quantity": 1, "unit": "tbsp", "is_optional": true }
    ],
    "steps": [
      { "step_number": 1, "instruction_text": "Chop the onion" },
      { "step_number": 2, "instruction_text": "Add the eggs and stir until set" }
    ]
  }
]`;

/** Instructions + samples for Admin → Bulk import (recipes). */
export function RecipeImportGuide() {
  return (
    <FormatGuide
      title="How to prepare your file (instructions and sample files)"
      notice={
        <div className="flex flex-col gap-1">
          <p className="flex items-center gap-2 font-semibold"><Clock aria-hidden className="h-4 w-4" /> Time limit: every recipe must take 60 minutes or less in total.</p>
          <ul className="list-disc pl-5">
            <li><C>prep_time_mins</C> 0–60 and <C>cook_time_mins</C> 1–60, <strong>and prep + cook ≤ 60</strong>.</li>
            <li>A recipe over the limit is rejected (e.g. prep 20 + cook 75 = 95 → <em>invalid</em>; prep 15 + cook 45 = 60 → valid). The other recipes in the file still import.</li>
            <li>Bachelor Chef is for quick everyday meals, so slow-cooked dishes (most beef curries, biryani, stews) need a quicker version, e.g. with a pressure cooker, or must be left out.</li>
          </ul>
        </div>
      }
      steps={[
        <>Download a <strong>sample file</strong> below (CSV or JSON) and open it. For CSV, Excel or Google Sheets works; save as <strong>CSV UTF-8 (comma delimited)</strong>.</>,
        <>Write <strong>one recipe per row</strong> (CSV) or <strong>one object per recipe</strong> (JSON). Up to <strong>500 recipes</strong> and 5 MB per file.</>,
        <>Use ingredient names that are already in <strong>Admin → Ingredients</strong> (case and spacing don&apos;t matter). A name that isn&apos;t found is created automatically as <em>Uncategorized</em>, so add new ingredients first with <strong>Bulk add</strong> if you want the right category.</>,
        <>Upload the file and click <strong>Validate &amp; preview</strong>. Nothing is saved yet; every problem is listed with its row number.</>,
        <>Fix any errors and upload again, or click <strong>Import</strong> to add the valid recipes. They are published straight away as official recipes. A recipe whose title already exists is skipped, so importing the same file twice is safe.</>,
      ]}
      csv={{
        intro: <>The first line must be this <strong>header</strong> (columns can be in any order). Put a value in <C>&quot;double quotes&quot;</C> if it contains a comma.</>,
        fields: [
          { field: "title", required: true, rules: "3–150 characters. Must be unique among official recipes.", example: "Egg Bhurji Toast" },
          { field: "description", required: false, rules: "Up to 5000 characters.", example: "Spicy scrambled eggs on toast" },
          { field: "prep_time_mins", required: true, rules: "Whole number of minutes, 0–60.", example: "5" },
          { field: "cook_time_mins", required: true, rules: <>Whole number, 1–60. <strong>Prep + cook must be 60 or less</strong>, otherwise the row is rejected. The total time is calculated for you.</>, example: "8" },
          { field: "difficulty_level", required: true, rules: <><C>EASY</C>, <C>MEDIUM</C> or <C>HARD</C> (any letter case).</>, example: "EASY" },
          { field: "estimated_cost_bdt", required: true, rules: "Total cost in ৳ for base_servings (not per person). 0–100000, up to 2 decimals.", example: "70.00" },
          { field: "base_servings", required: "Optional (default 1)", rules: "How many people the recipe feeds, 1–20.", example: "2" },
          { field: "image_url", required: false, rules: <>An <strong>https</strong> link from {IMAGE_HOSTS}.</>, example: "https://images.unsplash.com/photo-…" },
          { field: "tags_pipe", required: false, rules: <>Up to 10 tags separated by <C>|</C>. New tags are created.</>, example: "Breakfast|Egg|Quick" },
          { field: "ingredients_csv", required: true, rules: <>1–50 items separated by <C>|</C>. Each item is <C>Name:quantity:unit</C>; add <C>:optional</C> for an optional ingredient. Quantity &gt; 0 (decimals allowed), unit up to 20 characters.</>, example: "Egg:3:pcs|Salt:0.5:tsp|Coriander Leaves:1:tbsp:optional" },
          { field: "instructions_pipe", required: true, rules: <>1–30 steps <strong>in order</strong>, separated by <C>|</C>. Each step up to 2000 characters.</>, example: "Chop the onion|Fry it for 2 minutes|Add the eggs" },
        ],
        example: RECIPE_CSV_EXAMPLE,
        notes: [
          <>Need a <C>|</C> or <C>:</C> inside a name or step? Write <C>\|</C> or <C>\:</C> (a backslash is <C>\\</C>).</>,
          <>Do not add columns such as <C>total_time_mins</C>, <C>status</C> or <C>is_official</C>: the server sets them.</>,
          <>Empty lines are ignored. The header line is not counted as a recipe.</>,
        ],
        sampleHref: "/samples/recipes-sample.csv",
      }}
      json={{
        intro: <>The file is a JSON <strong>array</strong> <C>[ … ]</C> of recipe objects. Keys use <C>snake_case</C>; numbers are written without quotes.</>,
        fields: [
          { field: "title", required: true, rules: "Text, 3–150 characters, unique among official recipes.", example: "\"Egg Bhurji Toast\"" },
          { field: "description", required: false, rules: "Text, up to 5000 characters.", example: "\"Spicy scrambled eggs\"" },
          { field: "prep_time_mins", required: true, rules: "Number 0–60.", example: "5" },
          { field: "cook_time_mins", required: true, rules: <>Number 1–60. <strong>Prep + cook ≤ 60</strong>, otherwise the recipe is rejected.</>, example: "8" },
          { field: "difficulty_level", required: true, rules: <><C>&quot;EASY&quot;</C>, <C>&quot;MEDIUM&quot;</C> or <C>&quot;HARD&quot;</C>.</>, example: "\"EASY\"" },
          { field: "estimated_cost_bdt", required: true, rules: "Number (not text): total ৳ cost for base_servings, 0–100000.", example: "70.00" },
          { field: "base_servings", required: "Optional (default 1)", rules: "Number 1–20.", example: "2" },
          { field: "image_url", required: false, rules: <>https link from {IMAGE_HOSTS}, or <C>null</C>.</>, example: "null" },
          { field: "tags", required: false, rules: "Array of up to 10 texts.", example: "[\"Breakfast\", \"Egg\"]" },
          { field: "ingredients", required: true, rules: <>Array of 1–50 objects: <C>name</C> (text), <C>quantity</C> (number &gt; 0), <C>unit</C> (text ≤ 20), <C>is_optional</C> (<C>true</C>/<C>false</C>, optional).</>, example: "{ \"name\": \"Egg\", \"quantity\": 3, \"unit\": \"pcs\" }" },
          { field: "steps", required: true, rules: <>Array of 1–30 objects: <C>instruction_text</C> (text ≤ 2000) and optionally <C>step_number</C> (give it to all steps or to none) and <C>image_url</C>.</>, example: "{ \"step_number\": 1, \"instruction_text\": \"Chop the onion\" }" },
        ],
        example: RECIPE_JSON_EXAMPLE,
        notes: [
          <>Unknown keys are reported as errors, so check the spelling (e.g. <C>difficulty_level</C>, not <C>difficulty</C>).</>,
          <>Keys like <C>id</C>, <C>status</C>, <C>is_official</C> or <C>created_by</C> are rejected; <C>total_time_mins</C> is ignored.</>,
          <>Tip: paste your file into a JSON validator if the preview says &quot;malformed JSON&quot; (often a missing comma or bracket).</>,
        ],
        sampleHref: "/samples/recipes-sample.json",
      }}
    />
  );
}

const INGREDIENT_CSV_EXAMPLE = `name,category,default_unit
Red Lentils,Legumes,cup
"Chili, dried",Spices,pcs
Paneer,Dairy,g
Bay Leaf,,pcs`;

const INGREDIENT_JSON_EXAMPLE = `[
  { "name": "Red Lentils", "category": "Legumes", "default_unit": "cup" },
  { "name": "Chili, dried", "category": "Spices", "default_unit": "pcs" },
  { "name": "Paneer", "category": "Dairy", "default_unit": "g" },
  { "name": "Bay Leaf" }
]`;

/** Instructions + samples for Admin → Ingredients → Bulk add. */
export function IngredientImportGuide() {
  return (
    <FormatGuide
      title="How to prepare your file (instructions and sample files)"
      defaultOpen={false}
      steps={[
        <>Download a <strong>sample file</strong> below and add <strong>one ingredient per row</strong> (CSV) or <strong>one object per ingredient</strong> (JSON). Up to 500 per file.</>,
        <>Upload it and click <strong>Check file</strong>. Each row is marked <em>Will be added</em>, <em>Already exists</em>, <em>Duplicate in file</em> or <em>Invalid</em>. Nothing is saved yet.</>,
        <>Click <strong>Add N ingredients</strong>. Only the new ones are added; existing names (ignoring case and spacing) are skipped, so uploading the same file twice is safe.</>,
      ]}
      csv={{
        intro: <>The first line must be the header <C>name,category,default_unit</C>. Only <C>name</C> is required; leave a cell empty if you don&apos;t know it.</>,
        fields: [
          { field: "name", required: true, rules: "1–80 characters. Unique ignoring case and spacing.", example: "Red Lentils" },
          { field: "category", required: false, rules: "Up to 40 characters. Re-use existing categories (e.g. Spices, Dairy, Legumes, Vegetables).", example: "Legumes" },
          { field: "default_unit", required: false, rules: "Up to 20 characters: the unit you usually measure it in.", example: "cup" },
        ],
        example: INGREDIENT_CSV_EXAMPLE,
        notes: [<>Put a name with a comma in quotes, e.g. <C>&quot;Chili, dried&quot;</C>.</>, "Other columns are not allowed."],
        sampleHref: "/samples/ingredients-sample.csv",
      }}
      json={{
        intro: <>A JSON <strong>array</strong> of objects. <C>defaultUnit</C> is also accepted instead of <C>default_unit</C>.</>,
        fields: [
          { field: "name", required: true, rules: "Text, 1–80 characters.", example: "\"Red Lentils\"" },
          { field: "category", required: false, rules: "Text up to 40 characters, or null.", example: "\"Legumes\"" },
          { field: "default_unit", required: false, rules: "Text up to 20 characters, or null.", example: "\"cup\"" },
        ],
        example: INGREDIENT_JSON_EXAMPLE,
        notes: ["Values must be text (in quotes) or null. Unknown keys make that row invalid."],
        sampleHref: "/samples/ingredients-sample.json",
      }}
    />
  );
}
