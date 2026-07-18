# BOOTH-003 视频预览取消 — 补删遗漏行 + 一致性验证 + 提交

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete the one remaining "相机 → 流媒体网关" row in section 5.3 of the requirements spec that the spec-writer missed, then verify full consistency across all three modified documents before committing.

**Architecture:** This is a pure documentation fix. The spec-writer already modified three documents (需求规格说明书 13处, 任务提示词包 5处, 差距分析报告 1处). One omission was found: the software interface table row for "相机 → 流媒体网关" in 需求规格说明书_v1.2.md §5.3 was not deleted. This plan fixes that omission, verifies no other video-related items were missed, and commits.

**Tech Stack:** Markdown documents, git, grep for verification.

---

### Task 1: Delete the remaining "相机 → 流媒体网关" row in §5.3 software interface table

**Files:**
- Modify: `docs/平台开发/需求规格说明书_v1.2.md:1335`

- [ ] **Step 1: Confirm the exact line to delete**

Read the current content around line 1335 to confirm the exact string:
```bash
sed -n '1333,1337p' docs/平台开发/需求规格说明书_v1.2.md
```

Expected output (exact):
```
| 适配器 ↔ EMQX | MQTT（内网） | 设备通信中转 |
| 相机 → EMQX | MQTTS（公网，主动外连） | 设备接入，TLS加密 + 设备级ACL |
| 相机 → 流媒体网关 | GB28181（公网，主动推流） | 视频流推送，NAT穿透 |

### 5.4 通信接口
```

- [ ] **Step 2: Delete the line**

Delete line 1335 (`| 相机 → 流媒体网关 | GB28181（公网，主动推流） | 视频流推送，NAT穿透 |`) by removing the exact string:

Old string:
```
| 相机 → EMQX | MQTTS（公网，主动外连） | 设备接入，TLS加密 + 设备级ACL |
| 相机 → 流媒体网关 | GB28181（公网，主动推流） | 视频流推送，NAT穿透 |

### 5.4 通信接口
```

New string:
```
| 相机 → EMQX | MQTTS（公网，主动外连） | 设备接入，TLS加密 + 设备级ACL |

### 5.4 通信接口
```

Use the edit tool to perform this replacement in `docs/平台开发/需求规格说明书_v1.2.md`.

- [ ] **Step 3: Verify the deletion**

Confirm the line is gone and the table looks correct:
```bash
sed -n '1333,1337p' docs/平台开发/需求规格说明书_v1.2.md
```

Expected output (exact):
```
| 适配器 ↔ EMQX | MQTT（内网） | 设备通信中转 |
| 相机 → EMQX | MQTTS（公网，主动外连） | 设备接入，TLS加密 + 设备级ACL |

### 5.4 通信接口
```

Also confirm the string no longer appears anywhere in the file:
```bash
grep -n "相机 → 流媒体网关" docs/平台开发/需求规格说明书_v1.2.md
```

Expected: no output (exit code 1).

---

### Task 2: Verify consistency across all three documents

**Files (read-only verification):**
- `docs/平台开发/需求规格说明书_v1.2.md`
- `docs/平台开发/开发任务提示词包_v1.2.md`
- `docs/平台开发/差距分析报告_v1.1.md`

#### 2.1 Verify 需求规格说明书_v1.2.md — all video-related items are properly handled

- [ ] **Step 2.1.1: Check V1.3 revision record exists**

```bash
grep -n "V1.3.*BOOTH-003.*本期取消" docs/平台开发/需求规格说明书_v1.2.md
```

Expected: at least one match near line 14 or 25 showing V1.3 revision with BOOTH-003 cancellation noted.

- [ ] **Step 2.1.2: Check BOOTH-003 section has cancellation annotation**

```bash
grep -n "BOOTH-003" docs/平台开发/需求规格说明书_v1.2.md
```

Expected output matches these lines with their status:
| Line | Content check |
|:---|:---|
| ~14 | V1.3 status line mentions BOOTH-003 cancellation |
| ~25 | V1.3 revision history entry |
| ~228 | Architecture note with V1.3 cancellation suffix |
| ~818 | `**功能编号**：BOOTH-003` — section heading preserved |
| ~819 | `**功能名称**：出入口视频预览（本期取消，下期重开）` — annotated |
| ~1550 | 确认项 #66 with cancellation note |

Every occurrence of "BOOTH-003" should either be in its own preserved section, in a revision record, or in a confirmation item with a cancellation note.

- [ ] **Step 2.1.3: Check communication table (5.2/§2.3) has video row annotated**

```bash
grep -n "岗亭端 → 流媒体网关" docs/平台开发/需求规格说明书_v1.2.md
```

Expected: exactly one match on a line containing both "WebRTC" and "本期取消".

- [ ] **Step 2.1.4: Check PERF-006 / REL-006 have cancellation annotations**

```bash
grep -n -E "PERF-006|视频预览延迟" docs/平台开发/需求规格说明书_v1.2.md
```

Expected: the PERF-006 row shows "本期取消，下期随监控摄像头方案重开" or equivalent.

```bash
grep -n "REL-006" docs/平台开发/需求规格说明书_v1.2.md
```

Expected: the REL-006 row should be checkable (it's about white list sync, not video — confirm it was NOT modified by mistake).

- [ ] **Step 2.1.5: Check hardware interface table (5.2) — 流媒体网关 row annotated**

```bash
grep -n "流媒体网关.*V1.3.*本期不部署" docs/平台开发/需求规格说明书_v1.2.md
```

Expected: exactly one match (the 流媒体网关 hardware row in §5.2 with V1.3 cancellation note).

- [ ] **Step 2.1.6: Check software interface table (5.3) — no remaining video rows**

```bash
grep -n "相机 → 流媒体网关" docs/平台开发/需求规格说明书_v1.2.md
```

Expected: no output (the row was deleted in Task 1).

Also confirm no other GB28181/WebRTC items remain in the 5.3 table:
```bash
sed -n '/### 5.3 软件接口/,/### 5.4 通信接口/p' docs/平台开发/需求规格说明书_v1.2.md
```

Review the output: the 5.3 table should contain only non-video rows (平台→适配器, 适配器→平台, 平台→岗亭端, 适配器↔EMQX, 相机→EMQX). No GB28181 streaming or WebRTC rows should remain.

- [ ] **Step 2.1.7: Check 确认项 #55 and #66 are properly annotated**

```bash
grep -n "| 55 |" docs/平台开发/需求规格说明书_v1.2.md
grep -n "| 66 |" docs/平台开发/需求规格说明书_v1.2.md
```

Expected:
- Row 55: mentions "视频预览（占位卡片，下期开放）" or equivalent.
- Row 66: mentions "本期取消" or "下期重开".

- [ ] **Step 2.1.8: Check GB28181 / WebRTC term definitions are preserved (not deleted)**

```bash
grep -n -E "GB28181|WebRTC" docs/平台开发/需求规格说明书_v1.2.md | head -20
```

Expected: term definitions (lines ~92-94) appear with full definitions, NOT deleted. Other occurrences (BOOTH-003 section, architecture notes, COMP-007) should have appropriate context (either preserved with annotation or explicitly noted as cancelled).

#### 2.2 Verify 开发任务提示词包_v1.2.md — all video-related items properly handled

- [ ] **Step 2.2.1: Check task 7-2 is replaced with cancellation notice**

```bash
sed -n '/## 任务包 7-2/,/## 任务包 7-3/p' docs/平台开发/开发任务提示词包_v1.2.md
```

Expected output: the section heading "## 任务包 7-2：云端流媒体网关与 GB28181 视频预览" followed by a blockquote cancellation notice containing "本期取消" and "下期随监控摄像头方案重开", and a note that the video placeholder card in task 4-3 is preserved. No implementation steps should remain.

- [ ] **Step 2.2.2: Check stage 7 SDKS note mentions BOOTH-003 cancellation**

```bash
grep -n -B2 -A5 "BOOTH-003" docs/平台开发/开发任务提示词包_v1.2.md
```

Expected: the match near line ~959 shows the SDK capability note ending with "视频方案下期随监控摄像头方案重开（本期取消 BOOTH-003）".

- [ ] **Step 2.2.3: Check task 7-3 has no remaining GB28181 verification items**

```bash
sed -n '/## 任务包 7-3/,/^---$/p' docs/平台开发/开发任务提示词包_v1.2.md
```

Review the output: task 7-3's goals/acceptance/constraints should only list PDNS cloud platform verification (臻识 C5), NOT any GB28181 streaming verification items. The 10 numbered test scenarios should not include video preview testing.

- [ ] **Step 2.2.4: Check task 4-3 video placeholder card is preserved (not deleted)**

```bash
grep -n -E "视频接入中|视频预览区|占位卡片" docs/平台开发/开发任务提示词包_v1.2.md
```

Expected: matches in task 4-3 section (around line ~715) showing the video placeholder card design is preserved ("视频接入中" placeholder text).

- [ ] **Step 2.2.5: Check task 7-1 (control link) is not modified**

```bash
grep -n -E "GB28181|WebRTC|视频|流媒体" docs/平台开发/开发任务提示词包_v1.2.md
```

Review the output: any matches outside of the stage 7 SDKS note, task 7-2 cancellation block, and task 4-3 placeholder card references should either be in headers/summaries or need investigation. Specifically confirm task 7-1 content has NO video-related additions or deletions.

#### 2.3 Verify 差距分析报告_v1.1.md — BOOTH-003 row properly updated

- [ ] **Step 2.3.1: Check BOOTH-003 status changed to ⚪ with cancellation note**

```bash
grep -n "BOOTH-003" docs/平台开发/差距分析报告_v1.1.md
```

Expected: exactly one match, containing `| BOOTH-003 视频预览 | ⚪ | 本期取消，下期重开（车牌识别相机画面不适合岗亭监控） |`.

- [ ] **Step 2.3.2: Verify no other rows were accidentally changed**

```bash
grep -n -E "取消|下期重开|本期不" docs/平台开发/差距分析报告_v1.1.md
```

Expected: only the BOOTH-003 row (and possibly a revision note in the header) should mention cancellation. No other feature rows (BOOTH-001, BOOTH-002, BOOTH-004—BOOTH-010, ADMIN-*, MINI-*) should have cancellation annotations.

#### 2.4 Check for 误删/误改 (unintended modifications)

- [ ] **Step 2.4.1: Verify GB28181 / WebRTC term definitions are still present**

```bash
grep -c "GB28181.*国标视频监控联网协议" docs/平台开发/需求规格说明书_v1.2.md
grep -c "WebRTC.*Web Real-Time Communication" docs/平台开发/需求规格说明书_v1.2.md
```

Expected: each returns `1` (term definitions preserved, not deleted).

- [ ] **Step 2.4.2: Verify only BOOTH-003 has cancellation/deferral annotation**

```bash
grep -n -E "BOOTH-[0-9]+.*(取消|下期)" docs/平台开发/需求规格说明书_v1.2.md
```

Expected: only BOOTH-003 (line ~819, `出入口视频预览（本期取消，下期重开）`) should appear. No other BOOTH features (BOOTH-001, BOOTH-002, BOOTH-004 through BOOTH-010) should have cancellation or deferral annotations.

- [ ] **Step 2.4.3: Verify COMP-007 row is intact (compliance item, not deleted)**

```bash
grep -n "COMP-007" docs/平台开发/需求规格说明书_v1.2.md
```

Expected: one match showing the COMP-007 compliance row exists. The row describes the streaming gateway requirement — it should remain as a reference, even though deployment is deferred.

- [ ] **Step 2.4.4: Verify PDNS content is untouched**

```bash
grep -c "PDNS" docs/平台开发/需求规格说明书_v1.2.md
grep -c "PDNS" docs/平台开发/开发任务提示词包_v1.2.md
```

Expected: both return positive counts (PDNS content preserved, not accidentally deleted).

- [ ] **Step 2.4.5: Verify REL-006 is untouched (white list sync, not video)**

```bash
grep -n "REL-006" docs/平台开发/需求规格说明书_v1.2.md
```

Expected: REL-006 row exists without cancellation annotation (it's about white list sync, unrelated to video).

---

### Task 3: Commit all changes

**Files to commit:**
- `docs/平台开发/需求规格说明书_v1.2.md` (includes the line deletion from Task 1)
- `docs/平台开发/开发任务提示词包_v1.2.md` (previously modified by spec-writer)
- `docs/平台开发/差距分析报告_v1.1.md` (previously modified by spec-writer)
- `docs/superpowers/specs/2026-07-18-booth003-cancel-video-preview-design.md` (the spec itself)

- [ ] **Step 3.1: Check current git status**

```bash
git status --short
```

Expected: the four files above appear as modified (M). No other unexpected files should be modified.

- [ ] **Step 3.2: Review the diff for all changes**

```bash
git diff --stat
```

Expected: only the four files listed above appear. Review the total line change counts — they should be modest documentation changes.

Then review the full diff:
```bash
git diff
```

Verify:
- The line 1335 deletion is the only change NOT already reviewed by the spec-writer (all other changes were spec-writer's work).
- All changes are documentation-only (no code changes).
- No accidental deletions or modifications outside the BOOTH-003 cancellation scope.

- [ ] **Step 3.3: Stage only the intended files**

```bash
git add docs/superpowers/specs/2026-07-18-booth003-cancel-video-preview-design.md \
        docs/平台开发/需求规格说明书_v1.2.md \
        docs/平台开发/开发任务提示词包_v1.2.md \
        docs/平台开发/差距分析报告_v1.1.md
```

- [ ] **Step 3.4: Verify staged files match expectation**

```bash
git diff --cached --stat
```

Expected: the four files appear with their change counts. No other files are staged.

- [ ] **Step 3.5: Commit with the specified message**

```bash
git commit -m "[BOOTH-003] docs: 出入口视频预览本期取消，待监控摄像头方案确定后下期重开"
```

Expected: commit succeeds. Output shows the commit hash and summary line.

- [ ] **Step 3.6: Verify the commit**

```bash
git log -1 --stat
```

Expected: shows the commit with the correct message and the four files listed.

- [ ] **Step 3.7: Final sanity check — confirm no residual issues**

```bash
git diff HEAD~1 -- docs/平台开发/需求规格说明书_v1.2.md | grep "相机 → 流媒体网关"
```

Expected: shows the line deletion (the diff should show `-| 相机 → 流媒体网关 | GB28181（公网，主动推流） | 视频流推送，NAT穿透 |` as a removed line).

```bash
git diff HEAD~1 -- docs/平台开发/需求规格说明书_v1.2.md | grep "^+" | grep -i -E "gb28181|webrtc|流媒体网关|视频预览" | grep -v -E "下期|取消|不启用|不部署|V1.3|占位卡片"
```

Expected: no output. Any `+` lines mentioning video-related terms should also mention cancellation/deferral/placeholder. This catches accidental additions of video capability descriptions without the proper annotation.
