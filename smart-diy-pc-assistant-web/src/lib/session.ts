import { ref } from "vue";
import { api } from "./api";
import type { User } from "../../../shared/types";
export const user = ref<User | null>(null);
export async function restore() {
  if (localStorage.getItem("smartdiy_token")) {
    try {
      user.value = await api.request("/api/auth/me");
    } catch {
      logout();
    }
  }
}
export function logout() {
  localStorage.removeItem("smartdiy_token");
  user.value = null;
}
export function authenticated() {
  if (!user.value) throw Error("请先登录后继续");
}
