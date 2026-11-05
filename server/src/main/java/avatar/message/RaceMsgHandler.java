package avatar.message;

import java.io.DataInputStream;

import avatar.constants.Cmd;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;
import avatar.server.RaceService;

/**
 * Handler Đua thú (GET_HANDLER 12) — bản fan để rỗng (mọi gói rơi vào
 * MessageHandler chung), bản port viết theo client gốc Pack5:
 * 1 xin trạng thái · 2 thông tin thú · 5 đặt cược · 8 lịch sử ·
 * 9 chat. Engine ở avatar.server.RaceService (docs/race-spec.md).
 */
public class RaceMsgHandler extends MessageHandler {

    public RaceMsgHandler(Session client) {
        super(client);
    }

    @Override
    public void onMessage(Message mss) {
        User us = this.client.user;
        try {
            switch (mss.getCommand()) {
                case Cmd.GET_HANDLER: {
                    // Rời trường đua: bỏ khỏi danh sách người xem trước
                    // khi Session đổi handler (mất cược nếu đang có cược
                    // chưa trả — đúng luật gốc).
                    if (us != null) RaceService.getInstance().leave(us);
                    super.onMessage(mss);
                    return;
                }
                case Cmd.GET_IMG_ICON: {
                    if (us != null) this.client.doGetImgIcon(mss);
                    return;
                }
                case 1: {
                    if (us != null) RaceService.getInstance().onRequestState(us);
                    return;
                }
                case 2: {
                    if (us != null) {
                        DataInputStream rd = mss.reader();
                        RaceService.getInstance().onPetInfo(us, rd.readByte());
                    }
                    return;
                }
                case 5: {
                    if (us != null) {
                        DataInputStream rd = mss.reader();
                        int petId = rd.readByte();
                        int money = rd.readInt();
                        boolean ok = RaceService.getInstance().onBet(us, petId, money);
                        if (!ok) {
                            us.getAvatarService().serverDialog(
                                "Không đặt cược được (hết giờ cược hoặc không đủ xu)");
                        }
                    }
                    return;
                }
                case 8: {
                    if (us != null) RaceService.getInstance().onHistory(us);
                    return;
                }
                case 9: {
                    if (us != null) {
                        DataInputStream rd = mss.reader();
                        RaceService.getInstance().onChat(us, rd.readUTF());
                    }
                    return;
                }
                default:
                    // Các gói khác trong handler đua: bỏ qua như client
                    // gốc (RaceMsgHandler gốc cũng chỉ xử lý 1/2/5/8/9).
                    return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
