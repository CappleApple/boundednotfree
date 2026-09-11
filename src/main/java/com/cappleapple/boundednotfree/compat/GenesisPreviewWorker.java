package com.cappleapple.boundednotfree.compat;

import com.cappleapple.boundednotfree.runtime.PreviewLayout;

/** Implemented by the optional Genesis worker mixin, with no link to Genesis classes. */
public interface GenesisPreviewWorker {
    void boundednotfree$setLayout(PreviewLayout layout);
}