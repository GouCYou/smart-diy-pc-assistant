import { ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
export function useTask() {
  const busy = ref(false),
    error = ref("");
  async function perform(action: () => Promise<unknown>, success = "") {
    busy.value = true;
    error.value = "";
    try {
      await action();
      if (success) ElMessage.success(success);
    } catch (e) {
      error.value = (e as Error).message;
      ElMessage.error(error.value);
    } finally {
      busy.value = false;
    }
  }
  async function confirm(message: string, action: () => Promise<unknown>) {
    try {
      await ElMessageBox.confirm(message, "请确认操作", {
        type: "warning",
        confirmButtonText: "确认",
        cancelButtonText: "取消",
      });
    } catch {
      return;
    }
    await perform(action, "操作成功");
  }
  return { busy, error, perform, confirm };
}
