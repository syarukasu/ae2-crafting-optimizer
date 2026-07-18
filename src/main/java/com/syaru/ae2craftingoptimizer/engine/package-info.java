/**
 * コンパイル済みレシピグラフ、long高速Planner、overflow時のBigInteger昇格を実装する計算核。
 * 曖昧な代替素材、循環、世代変更を証明できない場合は結果を採用せずAE2標準Plannerへ戻す。
 */
package com.syaru.ae2craftingoptimizer.engine;
