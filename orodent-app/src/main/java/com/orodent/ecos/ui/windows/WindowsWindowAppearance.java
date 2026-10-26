package com.orodent.ecos.ui.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public final class WindowsWindowAppearance {
    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_DLGMODALFRAME = 0x00000001;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_FRAMECHANGED = 0x0020;

    private WindowsWindowAppearance() { }

    /** Call after showing the main stage. Keeps its taskbar icon and native controls. */
    public static void hideTitleIcon(String title) {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return;
        try {
            User32 windows = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);
            long processId = ProcessHandle.current().pid();
            Pointer[] target = new Pointer[1];
            WindowVisitor visitor = (window, ignored) -> {
                IntByReference owner = new IntByReference();
                windows.GetWindowThreadProcessId(window, owner);
                if (Integer.toUnsignedLong(owner.getValue()) != processId || !windows.IsWindowVisible(window))
                    return true;
                char[] caption = new char[256];
                windows.GetWindowTextW(window, caption, caption.length);
                if (!title.equals(Native.toString(caption))) return true;
                target[0] = window;
                return false;
            };
            windows.EnumWindows(visitor, null);
            if (target[0] == null) throw new IllegalStateException("Finestra principale non trovata");

            // Glass assigns the same HICON to the caption and taskbar. A dialog-style
            // frame hides the caption icon without clearing that shared HICON.
            Native.setLastError(0);
            int style = windows.GetWindowLongW(target[0], GWL_EXSTYLE);
            if (style == 0 && Native.getLastError() != 0)
                throw new IllegalStateException("GetWindowLongW: " + Native.getLastError());
            Native.setLastError(0);
            int previous = windows.SetWindowLongW(target[0], GWL_EXSTYLE, style | WS_EX_DLGMODALFRAME);
            if (previous == 0 && Native.getLastError() != 0)
                throw new IllegalStateException("SetWindowLongW: " + Native.getLastError());
            if (!windows.SetWindowPos(target[0], null, 0, 0, 0, 0,
                    SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED))
                throw new IllegalStateException("SetWindowPos: " + Native.getLastError());
        } catch (RuntimeException | LinkageError failure) {
            // Cosmetic only: a native integration failure must never prevent startup.
            System.err.println("Impossibile nascondere l'icona del titolo: " + failure.getMessage());
        }
    }

    public interface WindowVisitor extends StdCallLibrary.StdCallCallback {
        boolean visit(Pointer window, Pointer parameter);
    }

    public interface User32 extends StdCallLibrary {
        boolean EnumWindows(WindowVisitor visitor, Pointer parameter);
        int GetWindowThreadProcessId(Pointer window, IntByReference processId);
        boolean IsWindowVisible(Pointer window);
        int GetWindowTextW(Pointer window, char[] text, int capacity);
        int GetWindowLongW(Pointer window, int index);
        int SetWindowLongW(Pointer window, int index, int value);
        boolean SetWindowPos(Pointer window, Pointer insertAfter, int x, int y, int width, int height, int flags);
    }
}
