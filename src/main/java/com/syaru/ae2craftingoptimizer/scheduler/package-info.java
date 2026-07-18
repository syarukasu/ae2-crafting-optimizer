/**
 * 巨大ジョブと小さいジョブを共存させるDeficit Round RobinとProvider経路キャッシュ。
 * 一つのCPUやジョブがtick予算を独占しないよう、操作数と経過時間の両方で打ち切る。
 */
package com.syaru.ae2craftingoptimizer.scheduler;
