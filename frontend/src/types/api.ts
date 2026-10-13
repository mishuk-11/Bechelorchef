// Typed mirrors of the backend DTOs (see backend/src/main/java/com/bachelorchef/dto).

export type Role = "USER" | "ADMIN";
export type UserStatus = "ACTIVE" | "SUSPENDED" | "DELETED";
export type Difficulty = "EASY" | "MEDIUM" | "HARD";
export type RecipeStatus = "DRAFT" | "PENDING" | "APPROVED" | "REJECTED" | "ARCHIVED";
export type ReviewStatus = "PENDING" | "APPROVED" | "REJECTED" | "REMOVED";
export type TimeCategory = "EXPRESS" | "BACHELOR_QUICK" | "EXTENDED";
export type GoalCountMode = "UNIQUE_RECIPES" | "COOK_EVENTS";
export type ImportStatus = "PREVIEWED" | "PROCESSING" | "COMPLETED" | "COMPLETED_WITH_ERRORS" | "FAILED" | "EXPIRED";
export type ReportTargetType = "RECIPE" | "REVIEW";
export type ReportStatus = "OPEN" | "DISMISSED" | "ACTIONED";

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  fieldErrors: Record<string, string>;
  traceId?: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UserProfile {
  id: number;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  role: Role;
  status: UserStatus;
  avatarUrl: string | null;
  bio: string | null;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
  user: UserProfile;
}

export interface AuthorSummary {
  id: number | null;
  displayName: string;
  avatarUrl: string | null;
}

export interface RecipeSummary {
  id: number;
  title: string;
  imageUrl: string | null;
  prepTimeMins: number;
  cookTimeMins: number;
  totalTimeMins: number;
  timeCategory: TimeCategory;
  timeCategoryLabel: string;
  difficulty: Difficulty;
  estimatedCostBdt: number;
  baseServings: number;
  ratingAvg: number;
  ratingCount: number;
  official: boolean;
  status: RecipeStatus;
  author: AuthorSummary | null;
  createdAt: string;
}

export interface IngredientLine {
  ingredientId: number;
  name: string;
  quantity: number;
  unit: string;
  optional: boolean;
  active: boolean;
}

export interface Step {
  stepNumber: number;
  instruction: string;
  imageUrl: string | null;
}

export interface Tag {
  id: number;
  name: string;
}

export interface RecipeDetail {
  recipe: RecipeSummary;
  description: string | null;
  imageAssetId: string | null;
  costPerServingBdt: number;
  ingredients: IngredientLine[];
  steps: Step[];
  tags: Tag[];
  moderationNote: string | null;
  approvedAt: string | null;
  updatedAt: string;
  viewer: { canEdit: boolean; isOwner: boolean; saved: boolean; myReviewId: number | null };
}

export interface RecipeRequest {
  title: string;
  description?: string | null;
  prepTimeMins: number;
  cookTimeMins: number;
  difficulty: Difficulty;
  estimatedCostBdt: number;
  baseServings: number;
  imageUrl?: string | null;
  imageAssetId?: string | null;
  tags: string[];
  ingredients: { ingredientId: number; quantity: number; unit: string; optional: boolean }[];
  steps: { instruction: string; imageUrl?: string | null; imageAssetId?: string | null }[];
  submit: boolean;
}

export interface PortionResponse {
  recipeId: number;
  baseServings: number;
  servings: number;
  estimatedTotalCostBdt: number;
  costPerServingBdt: number;
  ingredients: { ingredientId: number; name: string; quantity: number; unit: string; optional: boolean }[];
  note: string;
}

export interface Ingredient {
  id: number;
  name: string;
  category: string | null;
  defaultUnit: string | null;
  active: boolean;
}

export interface MatchRequest {
  ingredientIds: number[];
  maxTotalTimeMins: number;
  strict: boolean;
  difficulty?: Difficulty | null;
  maxCostBdt?: number | null;
  page?: number;
  size?: number;
}

export interface IngredientRef {
  id: number;
  name: string;
}

export interface MatchItem {
  recipe: RecipeSummary;
  matchPercent: number;
  requiredCount: number;
  matchedCount: number;
  matchedIngredients: IngredientRef[];
  missingIngredients: IngredientRef[];
  optionalIngredients: (IngredientRef & { have: boolean })[];
}

export interface MatchResponse {
  results: Page<MatchItem>;
  maxTotalTimeMins: number;
  strict: boolean;
  selectedIngredientCount: number;
  disclaimer: string;
}

export interface Review {
  id: number;
  recipeId: number;
  rating: number;
  comment: string | null;
  photoUrl: string | null;
  status: ReviewStatus;
  author: AuthorSummary;
  createdAt: string;
  updatedAt: string;
}

export interface MyReview {
  id: number;
  recipeId: number;
  recipeTitle: string;
  rating: number;
  comment: string | null;
  photoUrl: string | null;
  status: ReviewStatus;
  moderationReason: string | null;
  updatedAt: string;
}

export interface AdminReview extends Omit<Review, "updatedAt"> {
  recipeTitle: string;
  moderationReason: string | null;
  authorEmail: string;
}

export interface SavedRecipe {
  recipe: RecipeSummary;
  savedAt: string;
}

export interface HistoryEntry {
  id: number;
  recipe: { id: number; title: string; imageUrl: string | null; totalTimeMins: number; available: boolean };
  cookedOn: string;
  notes: string | null;
  createdAt: string;
}

export interface HistoryPage {
  page: Page<HistoryEntry>;
  totalCookEvents: number;
  distinctRecipes: number;
}

export interface Goal {
  id: number;
  title: string;
  targetCount: number;
  month: string;
  countMode: GoalCountMode;
  tag: Tag | null;
  maxTotalTimeMins: number | null;
  progress: number;
  percent: number;
  completed: boolean;
  completedAt: string | null;
  createdAt: string;
}

export interface GoalRequest {
  title?: string;
  targetCount?: number;
  month?: string;
  countMode?: GoalCountMode;
  tagId?: number | null;
  maxTotalTimeMins?: number | null;
}

export interface Report {
  id: number;
  targetType: ReportTargetType;
  targetId: number;
  targetSummary: string;
  recipeId: number | null;
  reason: string;
  status: ReportStatus;
  reporter: AuthorSummary;
  createdAt: string;
}

export interface ImportIssue {
  rowNumber: number | null;
  lineNumber: number | null;
  field: string | null;
  severity: "ERROR" | "WARNING";
  code: string;
  message: string;
  excerpt: string | null;
}

export interface ImportPreview {
  jobId: number;
  status: ImportStatus;
  fileType: "CSV" | "JSON";
  filename: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  rows: { rowNumber: number; lineNumber: number | null; title: string | null; valid: boolean; totalTimeMins: number | null; ingredientCount: number; stepCount: number }[];
  issues: ImportIssue[];
}

export interface ImportJob {
  id: number;
  filename: string;
  fileType: "CSV" | "JSON";
  status: ImportStatus;
  uploadedBy: AuthorSummary | null;
  startedAt: string;
  finishedAt: string | null;
  totalRows: number;
  validRows: number;
  importedRows: number;
  failedRows: number;
  skippedRows: number;
  summary: string | null;
}

export interface ImportJobDetail {
  job: ImportJob;
  issues: ImportIssue[];
}

export interface AdminUserRow {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  status: UserStatus;
  createdAt: string;
  recipeCount: number;
  reviewCount: number;
  cookCount: number;
}

export interface IngredientStat {
  ingredientId: number;
  name: string;
  cookCount: number;
  recipeCount: number;
}

export interface RecipeStat {
  recipeId: number;
  title: string;
  ratingAvg: number;
  ratingCount: number;
  cookCount: number;
}

export interface DashboardSummary {
  from: string;
  to: string;
  activeUsers: number;
  newUsers: number;
  activeCooks: number;
  approvedRecipes: number;
  pendingRecipes: number;
  pendingReviews: number;
  openReports: number;
  cookEvents: number;
  averageRating: number;
  approvedReviewCount: number;
  popularIngredients: IngredientStat[];
  topRatedRecipes: RecipeStat[];
  mostCookedRecipes: RecipeStat[];
  recentImports: ImportJob[];
}

export interface ActivityPoint {
  date: string;
  signups: number;
  cookEvents: number;
  reviews: number;
  recipesSubmitted: number;
}

export interface RecipeAnalytics {
  from: string;
  to: string;
  topRated: RecipeStat[];
  mostCooked: RecipeStat[];
  activity: ActivityPoint[];
}

export interface IngredientAnalytics {
  from: string;
  to: string;
  popular: IngredientStat[];
}

export interface AuditEntry {
  id: number;
  actorId: number | null;
  actorEmail: string | null;
  action: string;
  targetType: string;
  targetId: number | null;
  details: string | null;
  createdAt: string;
}

export interface MediaConfig {
  uploadsEnabled: boolean;
  allowedHosts: string[];
  maxBytes: number;
  allowedFormats: string[];
}

export interface UploadSignature {
  uploadUrl: string;
  cloudName: string;
  apiKey: string;
  timestamp: number;
  signature: string;
  folder: string;
  allowedFormats: string;
  maxBytes: number;
}

export type MediaPurpose = "RECIPE" | "STEP" | "AVATAR" | "REVIEW";
