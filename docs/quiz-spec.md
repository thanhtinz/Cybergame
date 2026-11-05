# Đố vui trên map — AnswerQuestion [PORT]

## Gốc 3.4.4 nói gì
- Type `AnswerQuestion` (chỉ có paint/update, không có Scr riêng):
  fields `question/answer/listQuest/titleAns/timeQuestion/point/
  idBoss/imgTime/imgPoint/imgButton/x/y/w/h/isClick/focus/
  timeTick` — một THỰC THỂ CÂU HỎI hiện trên map gắn với boss:
  bấm vào, trả lời trắc nghiệm trong thời gian đếm ngược, đúng
  được điểm. `GlobalService.doAnswerQuestion` xử lý gói
  ANSWERQUESTION = -97.
- Ngân hàng câu hỏi nằm ở server nhà phát hành — không có trong
  APK, không khôi phục được.
- Số -97 ở bản fan đã là REQUEST_DYNAMIC_PART -> port dùng -121.

## Thiết kế bản port
- Gói **-121 Cmd.QUIZ**: server phát sub 0 QUESTION cho cả khu
  {int id, UTF câu hỏi, 4 đáp án đã xáo trộn, byte 30 giây,
  int thưởng tối đa 2.000}; client trả sub 1 ANSWER {int id,
  byte đáp án}; server trả sub 3 FEEDBACK riêng {đúng/sai, xu
  thưởng, đáp án đúng} và sub 2 RESULT cho cả khu khi hết giờ.
- `avatar/service/QuizService.java`: luồng nền mỗi 180 giây chọn
  khu park đông người nhất và phát một câu (câu đầu sau 60 giây;
  ticker bật lười khi có client đầu tiên vào khu — sub 9 PING —
  hoặc admin phát tay bằng sub 8 FORCE, role >= 0). Mỗi người
  một lần trả lời mỗi câu; thưởng theo tốc độ: <=10s 2.000 xu,
  <=20s 1.200, còn lại 600.
- Ngân hàng 24 câu kiến thức phổ thông/Việt Nam tự soạn [PORT]
  trong QuizService.BANK (đáp án đúng luôn ở vị trí 1 trong dữ
  liệu, server xáo trộn trước khi gửi).
- Client: panel Đố vui (640x470) tự bật khi câu hỏi tới (trừ lúc
  đang trong mini game), thanh thời gian xanh->đỏ, 4 nút đáp án
  A-D; đúng thì đóng panel + báo xu ở chat Hệ thống, sai thì hiện
  đáp án đúng trong panel; RESULT đóng câu được báo ở chat Hệ
  thống kèm tên người đúng nhanh nhất.

## Kiểm chứng
- Test sống live_quiz_test.py **ALL PASS**: FORCE phát câu, cả
  hai người cùng khu nhận QUESTION đúng định dạng, FEEDBACK
  đúng/sai đúng, xu của người trả lời đúng tăng đúng số thưởng
  trong DB (100.000 -> 102.000).
- Kiểm mắt PASS: kiem-quiz-v1.png. Harness stage 157
  (debugOpenQuiz).
