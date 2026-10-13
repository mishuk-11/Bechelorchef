import { z } from "zod";

// Client-side mirrors of the server rules, for usability only. The API validates everything again.

const intIn = (min: number, max: number, label: string) =>
  z.number({ error: `${label} is required.` }).int(`${label} must be a whole number.`).min(min, `${label} must be ${min}–${max}.`).max(max, `${label} must be ${min}–${max}.`);

export const loginSchema = z.object({
  email: z.string().trim().min(1, "Email is required.").email("Enter a valid email address."),
  password: z.string().min(1, "Password is required."),
});
export type LoginForm = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(2, "Enter your name.").max(100),
    email: z.string().trim().email("Enter a valid email address.").max(254),
    password: z.string().min(8, "Use at least 8 characters.").max(72, "Use at most 72 characters."),
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, { path: ["confirmPassword"], message: "Passwords do not match." });
export type RegisterForm = z.infer<typeof registerSchema>;

const httpsUrl = z
  .string()
  .trim()
  .max(500)
  .refine((v) => v === "" || /^https:\/\/[^\s]+$/i.test(v), "Use an https:// image link.")
  .optional();

export const recipeSchema = z
  .object({
    title: z.string().trim().min(3, "Title must be 3–150 characters.").max(150, "Title must be 3–150 characters."),
    description: z.string().max(5000, "Description must be at most 5000 characters.").optional(),
    prepTimeMins: intIn(0, 60, "Prep time"),
    cookTimeMins: intIn(1, 60, "Cook time"),
    difficulty: z.enum(["EASY", "MEDIUM", "HARD"]),
    estimatedCostBdt: z
      .number({ error: "Estimated cost is required." })
      .min(0, "Cost cannot be negative.")
      .max(100000, "Cost is too large.")
      .refine((v) => Math.abs(v * 100 - Math.round(v * 100)) < 1e-6, "Use at most 2 decimals."),
    baseServings: intIn(1, 20, "Servings"),
    imageUrl: httpsUrl,
    imageAssetId: z.string().optional().nullable(),
    tags: z.array(z.string()).max(10, "Use at most 10 tags."),
    ingredients: z
      .array(
        z.object({
          ingredientId: z.number({ error: "Choose an ingredient." }).int().positive("Choose an ingredient."),
          name: z.string().optional(),
          quantity: z.number({ error: "Quantity is required." }).positive("Quantity must be greater than 0."),
          unit: z.string().trim().min(1, "Unit is required.").max(20, "Max 20 characters."),
          optional: z.boolean(),
        }),
      )
      .min(1, "Add at least one ingredient.")
      .max(50),
    steps: z
      .array(z.object({ instruction: z.string().trim().min(1, "Instruction is required.").max(2000), imageUrl: httpsUrl, imageAssetId: z.string().optional().nullable() }))
      .min(1, "Add at least one step.")
      .max(30),
  })
  .refine((v) => v.prepTimeMins + v.cookTimeMins <= 60, {
    path: ["cookTimeMins"],
    message: "Total time (prep + cook) must not exceed 60 minutes.",
  })
  .refine((v) => v.ingredients.some((i) => !i.optional), {
    path: ["ingredients"],
    message: "At least one ingredient must be required (not optional).",
  })
  .refine((v) => new Set(v.ingredients.map((i) => i.ingredientId)).size === v.ingredients.length, {
    path: ["ingredients"],
    message: "Each ingredient can be listed only once.",
  });
export type RecipeForm = z.infer<typeof recipeSchema>;

export const reviewSchema = z.object({
  rating: intIn(1, 5, "Rating"),
  comment: z.string().max(2000, "At most 2000 characters.").optional(),
  photoUrl: httpsUrl,
});
export type ReviewForm = z.infer<typeof reviewSchema>;

export const goalSchema = z.object({
  title: z.string().trim().min(3, "Title must be 3–120 characters.").max(120),
  targetCount: intIn(1, 30, "Target"),
  month: z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/, "Use the format YYYY-MM."),
  countMode: z.enum(["UNIQUE_RECIPES", "COOK_EVENTS"]),
  tagId: z.number().int().nullable().optional(),
  maxTotalTimeMins: z.number().int().min(1).max(60).nullable().optional(),
});
export type GoalForm = z.infer<typeof goalSchema>;

export const ALLOWED_IMPORT_MAX_BYTES = 5 * 1024 * 1024;
