import { apiRequest } from "./client";
import type {
  AdminReview,
  AdminUserRow,
  AuditEntry,
  AuthResponse,
  DashboardSummary,
  Goal,
  GoalRequest,
  HistoryEntry,
  HistoryPage,
  ImportJob,
  ImportJobDetail,
  ImportPreview,
  Ingredient,
  IngredientImportResult,
  IngredientAnalytics,
  MatchRequest,
  MatchResponse,
  MediaConfig,
  MediaPurpose,
  MyReview,
  Page,
  PortionResponse,
  RecipeAnalytics,
  RecipeDetail,
  RecipeRequest,
  RecipeStatus,
  RecipeSummary,
  Report,
  ReportTargetType,
  Review,
  Role,
  SavedRecipe,
  Tag,
  UploadSignature,
  UserProfile,
  UserStatus,
} from "@/types/api";

/** Every backend call the UI makes, in one typed place. */

export interface RecipeSearchParams {
  q?: string;
  ingredientId?: number;
  tagId?: number;
  difficulty?: string;
  source?: string;
  maxCostBdt?: number;
  maxTotalTimeMins?: number;
  sort?: string;
  page?: number;
  size?: number;
}

export const authApi = {
  login: (email: string, password: string) =>
    apiRequest<AuthResponse>("/auth/login", { method: "POST", body: { email, password } }),
  register: (fullName: string, email: string, password: string) =>
    apiRequest<AuthResponse>("/auth/register", { method: "POST", body: { fullName, email, password } }),
  logout: () => apiRequest<void>("/auth/logout", { method: "POST" }),
  me: () => apiRequest<UserProfile>("/auth/me"),
};

export const profileApi = {
  update: (body: Partial<Pick<UserProfile, "fullName" | "phoneNumber" | "avatarUrl" | "bio">> & { avatarAssetId?: string | null }) =>
    apiRequest<UserProfile>("/users/me", { method: "PATCH", body }),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<void>("/users/me/password", { method: "POST", body: { currentPassword, newPassword } }),
  deleteAccount: (password: string) => apiRequest<void>("/users/me", { method: "DELETE", body: { password } }),
  myRecipes: (status?: RecipeStatus, page = 0) =>
    apiRequest<Page<RecipeSummary>>("/users/me/recipes", { query: { status, page, size: 12 } }),
  myReviews: (page = 0) => apiRequest<Page<MyReview>>("/users/me/reviews", { query: { page, size: 10 } }),
};

export const recipeApi = {
  search: (params: RecipeSearchParams) => apiRequest<Page<RecipeSummary>>("/recipes", { query: { ...params } }),
  detail: (id: number) => apiRequest<RecipeDetail>(`/recipes/${id}`),
  portions: (id: number, servings: number) => apiRequest<PortionResponse>(`/recipes/${id}/portions`, { query: { servings } }),
  match: (body: MatchRequest) => apiRequest<MatchResponse>("/recipes/match", { method: "POST", body }),
  create: (body: RecipeRequest) => apiRequest<RecipeDetail>("/recipes", { method: "POST", body }),
  update: (id: number, body: RecipeRequest) => apiRequest<RecipeDetail>(`/recipes/${id}`, { method: "PATCH", body }),
  withdraw: (id: number) => apiRequest<void>(`/recipes/${id}`, { method: "DELETE" }),
  cook: (id: number, notes?: string) => apiRequest<HistoryEntry>(`/recipes/${id}/cook`, { method: "POST", body: { notes } }),
  reviews: (id: number, page = 0) => apiRequest<Page<Review>>(`/recipes/${id}/reviews`, { query: { page, size: 10 } }),
  addReview: (id: number, body: { rating: number; comment?: string; photoUrl?: string | null }) =>
    apiRequest<Review>(`/recipes/${id}/reviews`, { method: "POST", body }),
};

export const reviewApi = {
  update: (id: number, body: { rating: number; comment?: string; photoUrl?: string | null }) =>
    apiRequest<Review>(`/reviews/${id}`, { method: "PATCH", body }),
  remove: (id: number) => apiRequest<void>(`/reviews/${id}`, { method: "DELETE" }),
  report: (targetType: ReportTargetType, targetId: number, reason: string) =>
    apiRequest<void>("/reports", { method: "POST", body: { targetType, targetId, reason } }),
};

export const catalogApi = {
  ingredients: (query: string, page = 0, size = 20) => apiRequest<Page<Ingredient>>("/ingredients", { query: { query, page, size } }),
  tags: () => apiRequest<Tag[]>("/tags"),
};

export const personalApi = {
  saved: (page = 0) => apiRequest<Page<SavedRecipe>>("/users/me/saved-recipes", { query: { page, size: 12 } }),
  save: (recipeId: number) => apiRequest<void>(`/users/me/saved-recipes/${recipeId}`, { method: "PUT" }),
  unsave: (recipeId: number) => apiRequest<void>(`/users/me/saved-recipes/${recipeId}`, { method: "DELETE" }),
  history: (page = 0) => apiRequest<HistoryPage>("/users/me/cooked-history", { query: { page, size: 15 } }),
  updateHistory: (id: number, body: { cookedOn?: string; notes?: string }) =>
    apiRequest<HistoryEntry>(`/users/me/cooked-history/${id}`, { method: "PATCH", body }),
  deleteHistory: (id: number) => apiRequest<void>(`/users/me/cooked-history/${id}`, { method: "DELETE" }),
  goals: () => apiRequest<Goal[]>("/users/me/goals"),
  createGoal: (body: GoalRequest) => apiRequest<Goal>("/users/me/goals", { method: "POST", body }),
  updateGoal: (id: number, body: GoalRequest) => apiRequest<Goal>(`/users/me/goals/${id}`, { method: "PATCH", body }),
  deleteGoal: (id: number) => apiRequest<void>(`/users/me/goals/${id}`, { method: "DELETE" }),
};

export const mediaApi = {
  config: () => apiRequest<MediaConfig>("/media/config"),
  sign: (purpose: MediaPurpose) => apiRequest<UploadSignature>("/media/upload-signature", { method: "POST", body: { purpose } }),
};

export const adminApi = {
  dashboard: (from?: string, to?: string) => apiRequest<DashboardSummary>("/admin/dashboard/summary", { query: { from, to } }),
  recipeAnalytics: (from?: string, to?: string) => apiRequest<RecipeAnalytics>("/admin/analytics/recipes", { query: { from, to } }),
  ingredientAnalytics: (from?: string, to?: string) =>
    apiRequest<IngredientAnalytics>("/admin/analytics/ingredients", { query: { from, to } }),
  auditLogs: (page = 0) => apiRequest<Page<AuditEntry>>("/admin/audit-logs", { query: { page, size: 20 } }),

  recipes: (params: { status?: string; q?: string; source?: string; page?: number }) =>
    apiRequest<Page<RecipeSummary>>("/admin/recipes", { query: { ...params, size: 15 } }),
  createOfficial: (body: RecipeRequest) => apiRequest<RecipeDetail>("/admin/recipes", { method: "POST", body }),
  approve: (id: number, reason?: string) => apiRequest<RecipeDetail>(`/admin/recipes/${id}/approve`, { method: "POST", body: { reason } }),
  reject: (id: number, reason: string) => apiRequest<RecipeDetail>(`/admin/recipes/${id}/reject`, { method: "POST", body: { reason } }),
  archive: (id: number, reason?: string) => apiRequest<RecipeDetail>(`/admin/recipes/${id}/archive`, { method: "POST", body: { reason } }),
  restore: (id: number) => apiRequest<RecipeDetail>(`/admin/recipes/${id}/restore`, { method: "POST" }),

  reviewQueue: (status: string, page = 0) => apiRequest<Page<AdminReview>>("/admin/moderation/reviews", { query: { status, page, size: 10 } }),
  moderateReview: (id: number, action: "approve" | "reject" | "remove", reason?: string) =>
    apiRequest<void>(`/admin/moderation/reviews/${id}/${action}`, { method: "POST", body: { reason } }),
  reports: (status: string, page = 0) => apiRequest<Page<Report>>("/admin/moderation/reports", { query: { status, page, size: 10 } }),
  resolveReport: (id: number, action: "DISMISS" | "REMOVE_CONTENT", note: string) =>
    apiRequest<void>(`/admin/moderation/reports/${id}/resolve`, { method: "POST", body: { action, note } }),

  ingredients: (params: { query?: string; category?: string; page?: number }) =>
    apiRequest<Page<Ingredient>>("/admin/ingredients", { query: { ...params, size: 20 } }),
  categories: () => apiRequest<string[]>("/admin/ingredients/categories"),
  createIngredient: (body: { name: string; category?: string; defaultUnit?: string }) =>
    apiRequest<Ingredient>("/admin/ingredients", { method: "POST", body }),
  updateIngredient: (id: number, body: { name: string; category?: string; defaultUnit?: string }) =>
    apiRequest<Ingredient>(`/admin/ingredients/${id}`, { method: "PATCH", body }),
  setIngredientActive: (id: number, active: boolean) =>
    apiRequest<Ingredient>(`/admin/ingredients/${id}/${active ? "activate" : "deactivate"}`, { method: "POST" }),
  previewIngredientImport: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return apiRequest<IngredientImportResult>("/admin/ingredients/import/preview", { method: "POST", form });
  },
  importIngredients: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return apiRequest<IngredientImportResult>("/admin/ingredients/import", { method: "POST", form });
  },
  createTag: (name: string) => apiRequest<Tag>("/admin/tags", { method: "POST", body: { name } }),

  users: (params: { q?: string; status?: string; role?: string; page?: number }) =>
    apiRequest<Page<AdminUserRow>>("/admin/users", { query: { ...params, size: 20 } }),
  setUserStatus: (id: number, status: UserStatus, reason?: string) =>
    apiRequest<AdminUserRow>(`/admin/users/${id}/status`, { method: "PATCH", body: { status, reason } }),
  setUserRole: (id: number, role: Role) => apiRequest<AdminUserRow>(`/admin/users/${id}/role`, { method: "PATCH", body: { role } }),

  previewImport: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return apiRequest<ImportPreview>("/admin/imports/preview", { method: "POST", form });
  },
  confirmImport: (jobId: number) => apiRequest<ImportJobDetail>("/admin/imports", { method: "POST", body: { jobId } }),
  imports: (page = 0) => apiRequest<Page<ImportJob>>("/admin/imports", { query: { page, size: 10 } }),
  importDetail: (id: number) => apiRequest<ImportJobDetail>(`/admin/imports/${id}`),
};
