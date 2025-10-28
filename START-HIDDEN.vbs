Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName) & "\web-app"
WshShell.Run "cmd /c node server/index.js", 0, False
CreateObject("WScript.Shell").SendKeys "{TAB}"
WScript.Sleep 3000
WshShell.Run "http://localhost:3000", 1, False

