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
const assert = __importStar(require("assert"));
const vscode = __importStar(require("vscode"));
const commands_1 = require("../commands");
suite('Run command detection', () => {
    test('recognizes scripts and direct executables', () => {
        for (const command of [
            'python script.py',
            'python3.12 -m my_module',
            'py -3 script.py',
            'node app.js',
            'npm run start',
            'cargo run',
            './my-program',
            '.\\program.exe',
            '& "C:\\Program Files\\My App\\app.exe" --verbose'
        ]) {
            assert.ok((0, commands_1.isRunCommand)(command), command);
        }
    });
    test('ignores unrelated shell commands', () => {
        for (const command of ['cd src', 'ls -la', 'git status', 'echo python script.py']) {
            assert.ok(!(0, commands_1.isRunCommand)(command), command);
        }
    });
});
suite('Enabled command setting', () => {
    test('VS Code exposes the default setting', () => {
        assert.deepStrictEqual(vscode.workspace.getConfiguration('catClapping').get('enabledCommands'), ['*']);
    });
    test('the default wildcard keeps built-in detection', () => {
        assert.ok((0, commands_1.isCommandEnabled)('python script.py', ['*'], true));
        assert.ok(!(0, commands_1.isCommandEnabled)('git status', ['*'], false));
    });
    test('users can select npm or Python independently', () => {
        assert.ok((0, commands_1.isCommandEnabled)('npm run build', ['npm'], true));
        assert.ok((0, commands_1.isCommandEnabled)('npx tsc', ['npm'], true));
        assert.ok(!(0, commands_1.isCommandEnabled)('python script.py', ['npm'], true));
        assert.ok((0, commands_1.isCommandEnabled)('py -3 script.py', ['python'], true));
        assert.ok((0, commands_1.isCommandEnabled)('python3.12 script.py', ['py'], true));
        assert.ok(!(0, commands_1.isCommandEnabled)('npm run build', ['python'], true));
    });
    test('specific commands and empty lists are supported', () => {
        assert.ok((0, commands_1.isCommandEnabled)('git status', ['git'], false));
        assert.ok((0, commands_1.isCommandEnabled)('& "C:\\Apps\\myapp.exe"', ['myapp.exe'], true));
        assert.ok((0, commands_1.isCommandEnabled)('.\\myapp.exe', ['executable'], true));
        assert.ok(!(0, commands_1.isCommandEnabled)('npm run build', [], true));
    });
    test('debug sessions follow the same choice', () => {
        assert.ok((0, commands_1.isDebugSessionEnabled)({ type: 'debugpy' }, ['python']));
        assert.ok(!(0, commands_1.isDebugSessionEnabled)({ type: 'pwa-node' }, ['python']));
        assert.ok((0, commands_1.isDebugSessionEnabled)({ type: 'pwa-node' }, ['node']));
        assert.ok((0, commands_1.isDebugSessionEnabled)({ type: 'custom' }, ['debug']));
        assert.ok(!(0, commands_1.isDebugSessionEnabled)({ type: 'custom' }, []));
    });
});
suite('Debug exit detection', () => {
    test('celebrates only a successful program exit', () => {
        assert.ok((0, commands_1.isSuccessfulDebugExit)({ type: 'event', event: 'exited', body: { exitCode: 0 } }));
        assert.ok(!(0, commands_1.isSuccessfulDebugExit)({ type: 'event', event: 'exited', body: { exitCode: 1 } }));
        assert.ok(!(0, commands_1.isSuccessfulDebugExit)({ type: 'event', event: 'terminated' }));
    });
});
//# sourceMappingURL=extension.test.js.map