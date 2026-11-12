"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.activate = activate;
exports.deactivate = deactivate;
const vscode = __importStar(require("vscode"));
const child_process_1 = require("child_process");
const commands_1 = require("./commands");
let lastShown = 0;
function activate(context) {
    vscode.window.showInformationMessage('Cat Clapping extension activated!');
    console.log('Cat Clapping activated');
    const endListener = vscode.window.onDidEndTerminalShellExecution((event) => {
        const command = event.execution.commandLine.value;
        if (event.exitCode === 0 &&
            (0, commands_1.isCommandEnabled)(command, enabledCommands(), isBuildCommand(command.toLowerCase()) || (0, commands_1.isRunCommand)(command))) {
            celebrate(context);
        }
    });
    const taskListener = vscode.tasks.onDidEndTaskProcess((event) => {
        if (event.exitCode !== 0) {
            return;
        }
        const task = event.execution.task;
        const resource = typeof task.scope === 'object' ? task.scope.uri : undefined;
        if (!isCelebrationTask(task, enabledCommands(resource))) {
            return;
        }
        celebrate(context);
    });
    const debugTracker = vscode.debug.registerDebugAdapterTrackerFactory('*', {
        createDebugAdapterTracker: session => ({
            onDidSendMessage: message => {
                if ((0, commands_1.isSuccessfulDebugExit)(message) &&
                    (0, commands_1.isDebugSessionEnabled)(session.configuration, enabledCommands(session.workspaceFolder?.uri))) {
                    celebrate(context);
                }
            }
        })
    });
    context.subscriptions.push(endListener, taskListener, debugTracker);
}
function enabledCommands(resource) {
    const configured = vscode.workspace.getConfiguration('catClapping', resource ?? vscode.window.activeTextEditor?.document.uri).get('enabledCommands', ['*']);
    return Array.isArray(configured) ? configured.filter(value => typeof value === 'string') : ['*'];
}
function isCelebrationTask(task, enabled) {
    const execution = task.execution;
    if (execution instanceof vscode.ProcessExecution) {
        const command = `"${execution.process}"`;
        if ((0, commands_1.isCommandEnabled)(command, enabled, isBuildCommand(execution.process.toLowerCase()) ||
            (0, commands_1.isRunCommand)(execution.process) || /\.exe$/i.test(execution.process))) {
            return true;
        }
    }
    if (execution instanceof vscode.ShellExecution) {
        const rawCommand = execution.commandLine ?? execution.command;
        const command = typeof rawCommand === 'string' ? rawCommand : rawCommand?.value;
        if (command && (0, commands_1.isCommandEnabled)(command, enabled, isBuildCommand(command.toLowerCase()) || (0, commands_1.isRunCommand)(command))) {
            return true;
        }
    }
    const isNamedTask = task.group?.id === vscode.TaskGroup.Build.id ||
        task.group?.id === vscode.TaskGroup.Test.id ||
        /\b(build|compile|run|execute|test)\b/i.test(task.name);
    return isNamedTask && enabled.some(value => ['*', 'tasks'].includes(value.trim().toLowerCase()));
}
function celebrate(context) {
    if (showCatAtEditorTop(context)) {
        playSuccessSound(context);
    }
}
function isBuildCommand(command) {
    const buildCommands = [
        // JavaScript / TypeScript
        /\bnpm\s+run\s+build\b/,
        /\bnpm\s+run\s+compile\b/,
        /\byarn\s+build\b/,
        /\byarn\s+compile\b/,
        /\bpnpm\s+build\b/,
        /\bpnpm\s+compile\b/,
        /\bnpx\s+tsc\b/,
        /\btsc\b/,
        // Java
        /\bmvn\s/,
        /\bmvnw\s/,
        /\bgradle\s/,
        /\bgradlew\s/,
        // .NET
        /\bdotnet\s+build\b/,
        // Rust
        /\bcargo\s+build\b/,
        // Go
        /\bgo\s+build\b/,
        // C / C++
        /\bmake\b/,
        /\bcmake\b/,
        /\bg\+\+(?=\s|$)/,
        /\bclang\+\+(?=\s|$)/,
        /\bcl(\.exe)?\b/,
        /\bcmake\s+--build\b/,
        /\bninja\b/,
        /\bmake\b/,
        // Salesforce
        /\bsf\s+project\s+deploy\b/,
        /\bsfdx\s+force:source:deploy\b/
    ];
    return buildCommands.some(pattern => pattern.test(command));
}
function showCatAtEditorTop(context) {
    const now = Date.now();
    if (now - lastShown < 1500) {
        return false;
    }
    const editor = vscode.window.activeTextEditor;
    if (!editor) {
        return false;
    }
    lastShown = now;
    const gifPath = vscode.Uri.joinPath(context.extensionUri, 'media', 'cat-clapping.gif');
    const decorationType = vscode.window.createTextEditorDecorationType({
        before: {
            contentIconPath: gifPath,
            textDecoration: 'none; position: absolute; top: 8px; left: 50%; transform: translateX(-50%); pointer-events: none; z-index: 10;',
            width: '60px',
            height: '60px'
        },
        rangeBehavior: vscode.DecorationRangeBehavior.ClosedClosed
    });
    const placeAtTop = () => {
        const line = editor.visibleRanges[0]?.start.line ?? 0;
        const position = new vscode.Position(line, 0);
        editor.setDecorations(decorationType, [new vscode.Range(position, position)]);
    };
    placeAtTop();
    const scrollListener = vscode.window.onDidChangeTextEditorVisibleRanges(event => {
        if (event.textEditor === editor) {
            placeAtTop();
        }
    });
    setTimeout(() => {
        scrollListener.dispose();
        decorationType.dispose();
    }, 7000);
    return true;
}
function playSuccessSound(context, onFinished) {
    const audioPath = vscode.Uri.joinPath(context.extensionUri, 'media', 'cat-clapping.wav').fsPath;
    if (process.platform === 'win32') {
        const escapedPath = audioPath.replace(/'/g, "''");
        const command = `$player = New-Object System.Media.SoundPlayer; $player.SoundLocation = '${escapedPath}'; $player.Load(); $player.PlaySync();`;
        (0, child_process_1.execFile)('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', command], { windowsHide: true }, error => {
            if (error) {
                console.error('[Cat Clapping] Audio error:', error);
            }
            onFinished?.();
        });
        return;
    }
    const players = process.platform === 'darwin'
        ? [['afplay', [audioPath]]]
        : [['paplay', [audioPath]], ['aplay', [audioPath]]];
    const tryPlayer = (index) => {
        if (index >= players.length) {
            console.error('[Cat Clapping] No supported audio player found. Install paplay or aplay.');
            onFinished?.();
            return;
        }
        const [file, args] = players[index];
        (0, child_process_1.execFile)(file, args, error => {
            if (error) {
                tryPlayer(index + 1);
            }
            else {
                onFinished?.();
            }
        });
    };
    tryPlayer(0);
}
function deactivate() { }
//# sourceMappingURL=extension.js.map