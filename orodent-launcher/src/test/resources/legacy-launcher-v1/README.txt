Frozen, unmodified updater sources from Orodent-EcoS commit
9bf37313ccc02d2fe35fead253863ce4dafee0c3 (released launcher contract v1).

These resources are compiled into an isolated class loader by compatibility tests.
Keep them unchanged: they prove that an already installed launcher can verify and
install newly signed components, without receiving a replacement launcher first.
