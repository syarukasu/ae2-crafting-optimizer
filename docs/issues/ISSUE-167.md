# Issue #167: Planner・Snapshot・世代・キャッシュ境界を一貫化する

- GitHub Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/167
- 状態: Implemented / Gradle verified / GameTest pending
- 対象版: 1.5.x
- 対象ローダー: Forge 1.20.1 / NeoForge 1.21.1
- 関連Issue・PR: #61、#79、#90、#103、#109、#125、#156、#159、#161、#162、#164、#165

## 問題

Planner高速化とキャッシュの一部がAE2標準候補集合を変更し、Pattern取得後の世代更新を
検出できず、非同期workerからlive Gridを参照している。request dedupもrequesterを
32bitの`identityHashCode`だけで識別するため、異なる要求を同一Futureへ束ね得る。

## 再現と証拠

- `CraftingTreeCandidatePruningMixin`がAE2の`getCraftingFor`結果を削減してから
  `CraftingTreeNode`へ返す。
- `PatternLookupCache.put`はAE2が一覧を返した後で現在世代を読み、旧一覧を新世代として
  公開できる。
- `CraftingCalculationDeduplicator.RequestKey`はrequester class名と
  `System.identityHashCode`だけを保持する。
- stale再試行は既存の在庫Snapshotを保持したままgenerationだけを更新する。
- cold graph compile、Emitter確認、最終在庫とTopology検証がworkerからlive
  `ICraftingService`、Grid storage、Levelへ触れる。

## 期待結果

- ACOはAE2の候補集合、順序、クラフト可否、必要量、substitutionを変更しない。
- 一つのPlanner入力は、同一revisionで取得したimmutableなPattern、recipe、在庫、
  Emitter、入力妥当性だけで構成される。
- background PlannerはmutableなMinecraft/AE2 server stateへ触れない。
- cacheとin-flight Futureは完全なkeyとrevisionが一致する場合だけ共有される。

## 現在結果

候補削減と世代外Pattern replayは削除した。Planner入力はserver threadで取得した
immutable root graph、参照在庫、Emitter、recipe/pattern/config/storage revisionへ固定し、
workerはlive Grid、Level、BlockEntity、AE2 serviceを読まない。stale結果は新世代へ
付け替えず、通常long計画は所有権取得前にAE2へ戻し、wide計画は元の理由で失敗する。

## 所有権

- AE2が所有する状態: Pattern候補と順序、クラフト可否、通常計画、通常CPU、在庫変更
- ACOが所有する状態: immutable計画Snapshot、bounded cache、dedup、BigInteger sidecar、診断
- 任意アドオンが所有する状態: 固有CPU、Worker、構造、電力、進捗、実行完了
- fallback可能な境界: ACOが入力または実行所有権を取得する前のlong計画だけ

## 維持する不変条件

- AE2から取得した候補集合と順序を変更しない。
- cache valueは生成元revision以外で公開または再利用しない。
- 同一Futureを共有できるのは同一network、要求、設定、requester参照、revisionだけ。
- background threadはimmutable captureだけを読む。
- stale在庫へ新しいPattern/recipe世代を付け替えない。
- wide正本をlongへクランプしない。
- ownership取得後にAE2へfallbackしない。

## やってはいけないこと

- null check、`catch(Throwable)`、sleep、無制限retryで競合を隠す。
- AE2標準候補をprune、並べ替え、重複除去する。
- 古い在庫Snapshotを新しいgenerationへ再ラベルする。
- background workerからlive Grid、Level、BlockEntityを読む。
- 正しさのために毎回全network graphを再構築する。
- 内部異常を`CPU_TOO_SMALL`や`NO_COMPILED_PROGRAM`へ読み替える。

## 修正方針

1. PR #159の数式PlannerをPR #165の所有権整理後の骨格へ統合する。
2. AE2標準経路のcandidate pruningを削除し、Decision Programは候補順を保持する。
3. global Pattern lookup cacheを削除し、世代安全なDecision Programと計算内memoへ一本化する。
4. requesterを強い参照同一性で比較するdedup keyへ変更する。
5. server threadでrevision前後を検証しながらimmutable captureを取得する。
6. graph compile、Topology証明、参照在庫再検証をcapture上のpure処理へ移す。
7. 在庫revisionをnetwork単位の専用Trackerへ分離し、計算開始前のAE2在庫cache更新、
   `NetworkStorage`の成立済みmutation、mount/unmountを同じrevisionへ集約する。
8. stale時に旧在庫へ新世代を付けた再試行は行わない。long計画は所有権取得前に
   AE2へ戻し、wide計画は元の理由を保持して明示失敗する。
9. workerへはrevision tokenと不変在庫値だけを渡し、`Grid`から現在値を読み直さない。
10. 世代counterはwrapさせず、上限到達時はABA一致を作る前に明示失敗する。
11. cache、dedup、plannerの時間と件数をboundedな診断へ追加する。

## 実装前チェック

- [x] `docs/PROJECT_CHARTER.md`を読んだ
- [x] `docs/REGRESSION_HISTORY.md`を読んだ
- [x] 関連クラスと既存試験を読んだ
- [x] 再現条件を試験へ変換した
- [x] 所有権とfallback境界を確定した
- [x] 禁止事項を明記した
- [x] Forge/NeoForgeの適用範囲を確定した

## 試験計画

- 単体試験: 候補順保持、requester参照分離、cache revision、immutable capture
- 境界試験: generation変更中のcapture/compile、planner完了直前の変更、wide辞退理由
- 故障・取消・復旧試験: dedup subscriber取消、delegate例外、stale結果破棄
- ビルド: 両版`clean test`、`clean build`、issue regression manifest、`git diff --check`
- GameTestまたはユーザー側確認: fixtureを追加し、実行は別途ユーザー側確認

## 実装結果

- AE2のPattern候補集合と順序を変更したcandidate pruningとglobal lookup cacheを削除した。
- root到達範囲だけをserver threadで固定し、workerへ不変graphと参照在庫だけを渡した。
- pattern、recipe、config、storage revisionをcapture、dedup、cache、完了結果へ通した。
- requesterとaction sourceは32bit hashではなく参照同一性で比較する。
- 同一requestのin-flight Futureは同一service、要求、設定、参照、全revisionの場合だけ共有する。
- subscriber単位の取消を分離し、最後のsubscriberが離れた場合だけdelegateを取消する。
- 完了Plan cacheは独立したAE2 Counterを毎回materializeし、storage revision変更で失効する。
- Root Program cacheは256件かつ合計1,048,576ノードのweighted LRUとし、対応Topologyも
  同時に退避する。上限超過Programはその計算だけで使い、常駐させない。
- 同一計算内のAE2候補、Emitter、Fuzzy候補、返却物、入力妥当性だけをmemo化し、
  pattern/recipe世代変更時に破棄する。
- shared DAGはexact byte会計の一意性を証明できないため高速wide経路から明示的に辞退する。
- stale、graph不一致、内部失敗、取消、timeoutをCPU容量不足へ読み替えない。
- capture、compile、cache、dedup、planner、stale rejectionをbounded diagnosticsへ追加した。

## 検証結果

- NeoForge 1.21.1 / Java 21: 122 suites、494 tests、failure 0、error 0、skip 0。
- `verifyIssueRegressionManifest`: 成功。
- `clean build --no-build-cache`: 成功。
- 1,000ノード直列Planner、40回warmup、120回計測の同一JVM相対値:
  compiled 28.669 ms、旧Map型296.586 ms、10.35倍。割り当て15.245 MiB対
  123.081 MiB、8.07分の1。
- `git diff --check`: 成功。
- Minecraft起動とGameTest実行は行っていない。固定fixtureは
  `evidence/ISSUE-167-GAMETEST-FIXTURE.md`、Matrix状態は`PENDING`のまま。

## 完了

- PR: Forge 1.20.1 #168 / NeoForge 1.21.1 #169（Draft）
- マージコミット: 未マージ
- 修正版: Draft検証ブランチ
- リリース: 未リリース
