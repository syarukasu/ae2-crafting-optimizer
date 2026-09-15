package com.syaru.ae2craftingoptimizer.access;

/** 一つのAE2 StorageServiceが所有する計画用revisionへのMixin境界。 */
public interface StorageRevisionAccess {
    long aco$captureStorageRevision();

    long aco$currentStorageRevision();

    void aco$advanceStorageRevision();
}
