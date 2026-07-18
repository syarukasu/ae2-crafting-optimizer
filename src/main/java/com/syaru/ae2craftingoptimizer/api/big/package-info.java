/**
 * longを超えるクラフト容量と進捗をBigIntegerで扱う、明示登録型CPU Host API。
 * 通常AE2へは安全なlong範囲の実行Windowだけを渡し、未対応CPUの数値表現は変更しない。
 */
package com.syaru.ae2craftingoptimizer.api.big;
