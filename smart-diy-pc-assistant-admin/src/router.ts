import { createRouter, createWebHistory } from "vue-router";
export default createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", component: () => import("./views/Dashboard.vue") },
    { path: "/users", component: () => import("./views/Users.vue") },
    { path: "/hardware", component: () => import("./views/Hardware.vue") },
    { path: "/news", component: () => import("./views/News.vue") },
    ...["community", "feedback", "notifications"].map((kind) => ({
      path: `/${kind}`,
      component: () => import("./views/Moderation.vue"),
      props: { kind },
    })),
    ...["agent", "knowledge", "runs"].map((kind) => ({
      path: kind === "agent" ? "/assistant" : `/${kind}`,
      component: () => import("./views/Agent.vue"),
      props: { kind },
    })),
    { path: "/settings", component: () => import("./views/System.vue") },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});
