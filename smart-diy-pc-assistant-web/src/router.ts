import { createRouter, createWebHistory } from "vue-router";
export default createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: "/shared/:token", component: () => import("./views/SharedConversation.vue") },
    { path: "/", component: () => import("./views/Chat.vue") },
    { path: "/auth", component: () => import("./views/Auth.vue") },
    { path: "/builds", component: () => import("./views/Builds.vue") },
    {
      path: "/community",
      component: () => import("./views/Feed.vue"),
      props: { kind: "community" },
    },
    {
      path: "/community/:id",
      component: () => import("./views/Detail.vue"),
      props: { kind: "community" },
    },
    {
      path: "/news",
      component: () => import("./views/Feed.vue"),
      props: { kind: "news" },
    },
    {
      path: "/news/:id",
      component: () => import("./views/Detail.vue"),
      props: { kind: "news" },
    },
    { path: "/account", component: () => import("./views/AccountHome.vue") },
    { path: "/profile", component: () => import("./views/Account.vue"), props: { section: "profile" } },
    { path: "/preferences", component: () => import("./views/Account.vue"), props: { section: "preferences" } },
    { path: "/settings", component: () => import("./views/Settings.vue") },
    { path: "/users/:id", component: () => import("./views/Space.vue") },
    {
      path: "/notifications",
      component: () => import("./views/Notifications.vue"),
    },
    { path: "/feedback/mine", component: () => import("./views/Feedback.vue"), props: { view: "mine" } },
    { path: "/feedback", component: () => import("./views/Feedback.vue") },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});
