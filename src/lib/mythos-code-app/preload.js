const { contextBridge, ipcRenderer } = require("electron");
const inv = (ch) => (...a) => ipcRenderer.invoke(ch, ...a);
contextBridge.exposeInMainWorld("mythos", {
  getCfg: inv("cfg:get"), setCfg: inv("cfg:set"),
  open: inv("open"), pickFolder: inv("pickFolder"), pickFiles: inv("pickFiles"),
  tool: inv("tool"), systemPrompt: inv("prompt:system"), prompts: inv("prompts"), abort: inv("abort"),
  onOutput: (fn) => ipcRenderer.on("tool-output", (_e, chunk) => fn(chunk)),
  working: inv("working"), notify: inv("notify"), push: inv("push"),
  chats: { list: inv("chats:list"), get: inv("chats:get"), save: inv("chats:save"), remove: inv("chats:delete"), search: inv("chats:search") },
  memory: { get: inv("memory:get"), add: inv("memory:add") },
  git: { info: inv("git:info"), diff: inv("git:diff"), commit: inv("git:commit"), push: inv("git:push") },
});
