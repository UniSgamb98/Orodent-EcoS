package com.orodent.ecos.misurazioni;

import javafx.animation.PauseTransition;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

/** Enter from the pedal: short press acquires; 300 ms hold switches instrument once. */
final class PedalInput implements AutoCloseable {
    private final Node root;
    private final Runnable acquire;
    private final PauseTransition hold = new PauseTransition(Duration.millis(300));
    private final EventHandler<KeyEvent> handler = this::handle;
    private boolean down, switched;

    PedalInput(Node root, Runnable acquire, Runnable swap) {
        this.root = root;
        this.acquire = acquire;
        hold.setOnFinished(
                e -> {
                    if (down) {
                        switched = true;
                        swap.run();
                    }
                });
        root.addEventFilter(KeyEvent.ANY, handler);
    }

    private void handle(KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER) return;
        if (event.getEventType() == KeyEvent.KEY_PRESSED) {
            if (!down) {
                down = true;
                switched = false;
                hold.playFromStart();
            }
            event.consume();
        } else if (event.getEventType() == KeyEvent.KEY_RELEASED) {
            if (down) {
                down = false;
                hold.stop();
                if (!switched) acquire.run();
            }
            event.consume();
        }
    }

    void cancel() {
        hold.stop();
        down = false;
    }

    @Override
    public void close() {
        cancel();
        root.removeEventFilter(KeyEvent.ANY, handler);
    }
}
