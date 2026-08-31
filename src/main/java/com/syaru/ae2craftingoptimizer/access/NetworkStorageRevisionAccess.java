package com.syaru.ae2craftingoptimizer.access;

/** NetworkStorageのmutationを、その所有StorageServiceだけへ通知するMixin境界。 */
public interface NetworkStorageRevisionAccess {
    void aco$setStorageRevisionOwner(StorageRevisionAccess owner);

    void aco$advanceStorageRevisionOwner();
}
