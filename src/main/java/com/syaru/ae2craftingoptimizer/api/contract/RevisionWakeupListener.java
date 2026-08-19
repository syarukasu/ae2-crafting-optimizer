package com.syaru.ae2craftingoptimizer.api.contract;

@FunctionalInterface
public interface RevisionWakeupListener {
    void onRevision(BatchTargetRevision revision);
}
