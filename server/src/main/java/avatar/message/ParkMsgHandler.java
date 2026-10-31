
package avatar.message;

import avatar.constants.Cmd;
import avatar.network.Message;
import avatar.network.Session;
import avatar.server.ServerManager;
import avatar.server.Utils;
import avatar.service.ParkService;

public class ParkMsgHandler extends MessageHandler {

    private ParkService service;

    public ParkMsgHandler(Session client) {
        super(client);
        this.service = new ParkService(client);
    }

    @Override
    public void onMessage(Message mss) {
        if (mss == null) {
            return;
        }
        if (this.client.user == null) {
            return;
        }
        System.out.println("ParkMsgHandler: " + mss.getCommand());
        try {
            switch (mss.getCommand()) {
                case Cmd.WEDDING_BIGIN: {
                    this.client.getParkService().WEDDING_BIGINHanlder(this.client.user, mss);
                    break;
                }
                case Cmd.MENU_ROTATE: {
                    this.client.getAvatarService().HandlerMENU_ROTATE(this.client.user, mss);
                    break;
                }
                case Cmd.AVATAR_REQUEST_ADD_FRIEND:
                    this.client.getParkService().handleAddFriendRequest(mss);
                    break;
                case Cmd.CHAT_TO:
                    this.client.getAvatarService().chatToUser(mss);
                    break;
                case Cmd.AVATAR_JOIN_PARK:
                    ServerManager.joinAreaMessage(this.client.user, mss);
                    break;
                case Cmd.MOVE_PARK:
                    this.client.user.move(mss);
                    break;

                case Cmd.CHAT_PARK:
                    this.client.user.chat(mss);
                    break;

                case Cmd.AVATAR_FEEL:
                    this.client.user.doAvatarFeel(mss);
                    break;

                case Cmd.REQUEST_DYNAMIC_PART:
                    this.client.getAvatarService().requestPartDynaMic(mss);
                    break;
                case Cmd.REQUEST_JOIN_ANY:
                    this.client.getAvatarService().requestJoinAny(mss);
                    break;
                case Cmd.START_CAU_CA: //86
                    this.client.getParkService().handleStartFishing(mss);
                    break;
                case Cmd.QUANG_CAU: //82
                    this.client.getParkService().handleQuangCau(mss);//82

                    long startTime = System.currentTimeMillis();
                    System.out.println("Waiting started at: " + startTime);
                    Thread.sleep(Utils.nextInt(12000,18000));

                    long endTime = System.currentTimeMillis();
                    System.out.println("Waiting ended at: " + endTime);
                    System.out.println("Elapsed time: " + (endTime - startTime) + " ms");
                    this.client.getParkService().onCanCau(); // 91
                    break;
                case Cmd.CAU_CA_XONG: //85
                    this.client.getParkService().CauCaXong();
                    break;
                case Cmd.CAU_THANH_CONG: //84
                    this.client.getParkService().CauThanhCong();//84
                    break;
                default:
                    System.out.println("ParkMsgHandler: " + mss.getCommand());
                    super.onMessage(mss);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
