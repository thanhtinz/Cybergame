# Aincrad (hub mini-game id 2) — nghiên cứu từ bản chính 3.4.4

Ngày đào: 2026-11-03 (từ `~/workspace/apk-avatardk-new/deep/metadata/all_types_full.json`
+ `string_literals.txt` + `deep/textures/`). Mức độ: **hình hài + giao thức rõ,
luật chi tiết còn là suy luận** — muốn chắc 100% phải dịch IL2CPP native
(libil2cpp.so), chưa làm.

## Ba type của game

- `AincradMsgHandler` — lệnh con (field const): `AINCRADGAME_START`,
  `AINCRADGAME_PLAYER_MOVE`, `AINCRADGAME_PLAYER_SHOW`,
  `AINCRADGAME_PLAYER_REMOVE_GHOST`; method: `start`, `playerShow`,
  `playerMove`, `playerRemoveGhost`, `onMessage`.
  Log strings gốc: "Err Nhan AINCRADGAME_START:", "Err >>> aincrad_sendMove:"…
- `AincradService` (client-side service): `aincrad_sendDoor`.
- `AincradGameScr` — màn hình game. Field cho thấy cấu trúc:
  - Trạng thái: `STT_NON`, `STT_GHOST` (người chơi có trạng thái "ma").
  - Cửa: `vecDoor`, `createDoor`, `pnt_door`, `upd_door`, `updKey_door`,
    `checkDoor`, `sendDoor`, `setDoorPrepare`, `upd_doorprepare`,
    `setDoorOpen`; `imgMaindoor`, `imgSubdoor`, `nDoor`, `nsubDoor`,
    `wdoor`, `hdoor`, `xmaindoor`, `ymaindoor`, `timeClickDoor`.
  - Tầng: `FLOOR_WINNER`, `setFloor`, `numFL`, `imgFL`, `idWinner`.
  - Ma: `imgGhost`, `addGhost`, `removeGhost`, `upd_ghost`,
    `timeghosttest`, `isghostTest`, `timeTest`, `tesst`.
  - Khác: `imgFenci` (hàng rào), `imgEx` (biển exit), `a_xfen1/a_xfen2`,
    `limx_left/limx_right` (giới hạn đi ngang), `setMove`, `updCamera`.

## Asset (đủ bộ, chỉ 9 ảnh trong bundle)

| File | Kích thước | Vai trò suy luận |
|---|---|---|
| `1FL_Maindoor.png` / `1FL_MaindoorF.png` | 58×106 | cửa chính tầng 1 + biến thể F |
| `1FL_Subdoor.png` / `1FL_SubdoorF.png` | 63×86 / 64×86 | cửa phụ tầng 1 + biến thể F |
| `2FL_Fencing.png` | 100×33 | hàng rào tầng 2 (imgFenci) |
| `2FL_Maindoor.png` / `2FL_MaindoorF.png` | 67×77 | cửa chính tầng 2 + biến thể F |
| `3FL_Ex.png` | 125×31 | biển EXIT tầng 3 (imgEx) — đích cuối |
| `Ghost.png` | 68×160 | hình ma (cao, bằng ~1 nhân vật) |

Nguồn: `~/workspace/apk-avatardk-new/deep/textures/`. Chỉ có cửa tầng 1–2 và
exit tầng 3 → **tháp 3 tầng**.

## Luật chơi — suy luận từ cấu trúc (chưa kiểm chứng bằng gói live)

- Thi leo tháp 3 tầng theo khung hub (2 ghế, lượt, khán giả như Tàu Chiến).
- Mỗi tầng có **1 cửa chính + nhiều cửa phụ** xếp ngang; người chơi đi
  ngang (limx_left/right, setMove) tới một cửa rồi mở (timeClickDoor,
  checkDoor, sendDoor → server `aincrad_sendDoor`).
- Biến thể **F** của cửa: nhiều khả năng là cửa "giả/sập" — mở trúng cửa
  đúng thì lên tầng (setFloor, numFL tăng), mở sai thì **hoá ma**
  (STT_GHOST + addGhost hiện hình Ghost) — và có lệnh
  `AINCRADGAME_PLAYER_REMOVE_GHOST` để gỡ trạng thái ma (hồi sinh/
  trừ ma cho người chơi?).
- Thắng: lên tới tầng 3 (biển EXIT) trước → `FLOOR_WINNER` + `idWinner`.
- `PLAYER_SHOW`: nhiều khả năng là gói server phát vị trí/trạng thái
  của người chơi còn lại (show = hiện lên màn hình đối phương/khán giả).

## Việc cần khi bắt tay dựng

1. Dịch IL2CPP các method chính (createDoor/checkDoor/upd_ghost) hoặc
   bắt gói live 3.4.4 (đang kẹt bắt tay 19129) để chốt: số cửa phụ mỗi
   tầng, lượt đi (đồng thời hay theo lượt), ma bị bao lâu/thua luôn?,
   REMOVE_GHOST do ai gửi.
2. Nếu không chốt được: dựng theo suy luận trên, ghi rõ [PORT] trong
   spec như đã làm với Tàu Chiến (bàn 10×10…), chờ Tín chơi bản chính
   đối chiếu.
3. Asset nhập thẳng 9 file trên vào `assets/minigame/`; khung phòng
   dùng lại MiniGameService (phong bì −113, gameId 2).
