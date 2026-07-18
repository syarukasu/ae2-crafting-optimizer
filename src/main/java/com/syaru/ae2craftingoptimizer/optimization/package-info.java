/**
 * 計算メモ化、各種キャッシュ、CPU・Gridのtick予算、失効処理をまとめる保守的最適化層。
 * キャッシュは正解を生成せず、世代や構造が変わった時点で破棄して元の判定へ戻す。
 */
package com.syaru.ae2craftingoptimizer.optimization;
