-- DEVELOPMENT SAMPLE CONTENT ONLY (loaded by the `dev` profile via spring.flyway.locations).
-- Recipes are flagged is_sample = TRUE and is_official = TRUE, have no author account,
-- and no fake reviews, cook events or analytics are created. Idempotent: re-running skips
-- recipes whose title already exists as a sample.

CREATE OR REPLACE FUNCTION pg_temp.seed_recipe(
    p_title TEXT, p_description TEXT, p_prep INT, p_cook INT, p_difficulty TEXT,
    p_cost NUMERIC, p_servings INT, p_ingredients TEXT, p_steps TEXT[], p_tags TEXT[]
) RETURNS VOID AS $$
DECLARE
    v_recipe_id BIGINT;
    v_item TEXT;
    v_parts TEXT[];
    v_pos INT := 0;
    v_ingredient_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM recipes WHERE is_sample AND normalized_title = lower(btrim(p_title))) THEN
        RETURN;
    END IF;

    INSERT INTO recipes (title, normalized_title, description, prep_time_mins, cook_time_mins, total_time_mins,
                         difficulty, estimated_cost_bdt, base_servings, is_official, is_sample, status, approved_at)
    VALUES (p_title, lower(btrim(p_title)), p_description, p_prep, p_cook, p_prep + p_cook,
            p_difficulty, p_cost, p_servings, TRUE, TRUE, 'APPROVED', now())
    RETURNING recipe_id INTO v_recipe_id;

    -- Ingredient format: name:quantity:unit[:optional] separated by |
    FOREACH v_item IN ARRAY string_to_array(p_ingredients, '|') LOOP
        v_parts := string_to_array(v_item, ':');
        v_pos := v_pos + 1;
        SELECT ingredient_id INTO v_ingredient_id FROM ingredients WHERE normalized_name = lower(btrim(v_parts[1]));
        IF v_ingredient_id IS NULL THEN
            RAISE EXCEPTION 'Seed ingredient not found: %', v_parts[1];
        END IF;
        INSERT INTO recipe_ingredients (recipe_id, ingredient_id, quantity, unit, is_optional, sort_order)
        VALUES (v_recipe_id, v_ingredient_id, v_parts[2]::NUMERIC, v_parts[3], coalesce(v_parts[4] = 'optional', FALSE), v_pos);
    END LOOP;

    FOR i IN 1 .. array_length(p_steps, 1) LOOP
        INSERT INTO recipe_steps (recipe_id, step_number, instruction_text) VALUES (v_recipe_id, i, p_steps[i]);
    END LOOP;

    INSERT INTO recipe_tags (recipe_id, tag_id)
    SELECT v_recipe_id, tag_id FROM tags WHERE normalized_name = ANY (SELECT lower(t) FROM unnest(p_tags) t);
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.seed_recipe('Egg Fried Rice', 'Turn leftover rice into a filling dinner in one pan.', 5, 10, 'EASY', 80.00, 2,
    'Cooked Rice:2:cup|Egg:2:pcs|Onion:1:pcs|Green Chili:1:pcs|Oil:1:tbsp|Salt:0.5:tsp|Soy Sauce:1:tbsp:optional',
    ARRAY['Heat oil in a pan and fry the chopped onion and chili for 2 minutes.',
          'Push aside, scramble the eggs in the same pan.',
          'Add the rice, salt and soy sauce; stir-fry on high heat for 4 minutes.'],
    ARRAY['Quick', 'Rice', 'Egg', 'Dinner', 'One Pan']);

SELECT pg_temp.seed_recipe('Masala Omelette', 'A spicy Bangladeshi-style omelette ready in minutes.', 3, 5, 'EASY', 35.00, 1,
    'Egg:2:pcs|Onion:0.5:pcs|Green Chili:1:pcs|Salt:0.25:tsp|Oil:1:tbsp|Coriander Leaves:1:tbsp:optional|Turmeric Powder:0.25:tsp:optional',
    ARRAY['Beat the eggs with chopped onion, chili, salt and turmeric.',
          'Pour into a hot oiled pan and cook 2 minutes per side.'],
    ARRAY['Quick', 'Breakfast', 'Egg', 'Bangladeshi', 'Budget']);

SELECT pg_temp.seed_recipe('Quick Egg Noodles', 'Instant noodles upgraded with egg and vegetables.', 3, 10, 'EASY', 60.00, 1,
    'Instant Noodles:1:pack|Egg:1:pcs|Green Chili:2:pcs|Onion:0.5:pcs|Oil:1:tsp|Carrot:0.5:pcs:optional',
    ARRAY['Boil the noodles for 3 minutes and drain, keeping a little water.',
          'Scramble the egg with onion and chili in a pan.',
          'Mix in the noodles and seasoning and toss for 2 minutes.'],
    ARRAY['Quick', 'Noodles', 'Egg', 'Snack']);

SELECT pg_temp.seed_recipe('Masala Oats', 'Savoury oats for a warm, cheap breakfast.', 2, 8, 'EASY', 45.00, 1,
    'Oats:1:cup|Water:2:cup|Onion:1:pcs|Tomato:1:pcs|Salt:0.5:tsp|Turmeric Powder:0.25:tsp|Green Chili:1:pcs:optional',
    ARRAY['Fry the chopped onion and tomato for 2 minutes.',
          'Add water, salt and turmeric and bring to a boil.',
          'Stir in the oats and cook for 5 minutes until thick.'],
    ARRAY['Quick', 'Breakfast', 'Vegetarian', 'Budget']);

SELECT pg_temp.seed_recipe('Aloo Bhaji', 'Simple spiced potato stir-fry, great with rice or bread.', 5, 15, 'EASY', 40.00, 2,
    'Potato:2:pcs|Onion:1:pcs|Green Chili:2:pcs|Turmeric Powder:0.5:tsp|Salt:0.5:tsp|Mustard Oil:2:tbsp',
    ARRAY['Cut potatoes into thin sticks and slice the onion.',
          'Heat mustard oil, add onion and chili and fry until soft.',
          'Add potatoes, turmeric and salt; cover and cook 12 minutes, stirring often.'],
    ARRAY['Bangladeshi', 'Vegetarian', 'Lunch', 'Budget']);

SELECT pg_temp.seed_recipe('Masoor Dal', 'Comforting red lentil dal, the classic student staple.', 5, 25, 'EASY', 55.00, 3,
    'Red Lentils (Masoor Dal):1:cup|Water:3:cup|Onion:1:pcs|Garlic:3:clove|Turmeric Powder:0.5:tsp|Salt:1:tsp|Oil:1:tbsp|Green Chili:2:pcs:optional',
    ARRAY['Rinse the lentils and boil with water, turmeric and salt for 20 minutes.',
          'Fry sliced onion and garlic in oil until golden.',
          'Pour the fried onion into the dal and simmer 3 more minutes.'],
    ARRAY['Bangladeshi', 'Vegetarian', 'Dinner', 'Budget', 'High Protein']);

SELECT pg_temp.seed_recipe('Cheese Toast', 'Crispy toast with melted cheese and pepper.', 2, 5, 'EASY', 50.00, 1,
    'Bread:2:slice|Cheese:2:slice|Butter:1:tbsp|Black Pepper:0.25:tsp:optional',
    ARRAY['Butter the bread and place cheese on top.',
          'Toast in a covered pan on low heat for 5 minutes until the cheese melts.'],
    ARRAY['Quick', 'Snack', 'Breakfast', 'Vegetarian']);

SELECT pg_temp.seed_recipe('Jhal Muri', 'No-cook spicy puffed rice snack.', 5, 1, 'EASY', 30.00, 2,
    'Puffed Rice (Muri):2:cup|Onion:0.5:pcs|Green Chili:1:pcs|Cucumber:0.5:pcs|Mustard Oil:1:tbsp|Salt:0.25:tsp|Peanuts:2:tbsp:optional|Lemon:0.5:pcs:optional',
    ARRAY['Finely chop the onion, chili and cucumber.',
          'Toss everything with the puffed rice, mustard oil and salt; serve immediately.'],
    ARRAY['Quick', 'Snack', 'No Cook', 'Bangladeshi', 'Vegetarian', 'Budget']);

SELECT pg_temp.seed_recipe('Tomato Egg Curry', 'Boiled eggs simmered in a quick tomato-onion gravy.', 5, 20, 'MEDIUM', 70.00, 2,
    'Egg:4:pcs|Tomato:2:pcs|Onion:1:pcs|Garlic:2:clove|Ginger:1:tsp|Turmeric Powder:0.5:tsp|Chili Powder:0.5:tsp|Salt:0.75:tsp|Oil:2:tbsp',
    ARRAY['Boil the eggs for 8 minutes, peel and set aside.',
          'Fry onion, garlic and ginger, then add tomato and spices.',
          'Cook until the oil separates, add a splash of water and the eggs.',
          'Simmer for 5 minutes.'],
    ARRAY['Egg', 'Dinner', 'Bangladeshi', 'High Protein']);

SELECT pg_temp.seed_recipe('Chicken Stir-Fry', 'Fast chicken and vegetable stir-fry with soy sauce.', 10, 12, 'MEDIUM', 180.00, 2,
    'Chicken:250:g|Onion:1:pcs|Carrot:1:pcs|Cabbage:1:cup|Garlic:2:clove|Soy Sauce:2:tbsp|Oil:1:tbsp|Black Pepper:0.5:tsp',
    ARRAY['Slice the chicken thinly and chop the vegetables.',
          'Stir-fry the chicken in hot oil for 6 minutes.',
          'Add vegetables, garlic, soy sauce and pepper; cook 5 minutes more.'],
    ARRAY['Dinner', 'High Protein', 'One Pan']);

SELECT pg_temp.seed_recipe('Banana Oat Smoothie', 'A filling breakfast drink, no stove needed.', 5, 1, 'EASY', 60.00, 1,
    'Banana:1:pcs|Milk:1:cup|Oats:0.25:cup|Honey:1:tbsp:optional',
    ARRAY['Blend banana, milk and oats until smooth.',
          'Sweeten with honey if you like and serve.'],
    ARRAY['Quick', 'Breakfast', 'No Cook', 'Vegetarian']);

SELECT pg_temp.seed_recipe('Tuna Pasta', 'Pantry pasta with canned tuna and tomato.', 5, 15, 'MEDIUM', 220.00, 2,
    'Pasta:200:g|Canned Tuna:1:can|Tomato:2:pcs|Garlic:2:clove|Oil:1:tbsp|Salt:1:tsp|Black Pepper:0.5:tsp|Cheese:1:slice:optional',
    ARRAY['Boil the pasta in salted water for 10 minutes and drain.',
          'Fry garlic and chopped tomato until saucy.',
          'Stir in tuna and pasta, season and serve.'],
    ARRAY['Lunch', 'Dinner', 'High Protein']);
