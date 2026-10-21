import { createApp } from "vue";
import { ElLoading } from "element-plus";

import "element-plus/dist/index.css";
import "element-plus/theme-chalk/el-loading.css";
import "element-plus/theme-chalk/el-message.css";
import "element-plus/theme-chalk/el-message-box.css";
import "element-plus/theme-chalk/dark/css-vars.css";
import "./style.css";
import "./theme.css";
import "./lib/theme";
import { slider } from "./directives/slider";
import router from "./router";
import App from "./App.vue";
createApp(App)
  .use(router)
  .directive("slider", slider)
  .directive("loading", ElLoading.directive)
  .mount("#app");
