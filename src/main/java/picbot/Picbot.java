package picbot;

import org.slf4j.Logger;
import org.slf4j.impl.SimpleLoggerFactory;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.groupadministration.LeaveChat;
import org.telegram.telegrambots.meta.api.methods.send.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.time.temporal.ChronoUnit.SECONDS;


public final class Picbot extends TelegramLongPollingBot {

    private static Logger logger = new SimpleLoggerFactory().getLogger("BotLogger");

    private final LocalDateTime dateTimeWork = LocalDateTime.now();
    private final AtomicLong numberOfSendPictures = new AtomicLong(0);
    private final AtomicLong numberOfLikedPictures = new AtomicLong(0);
    private final Random random = new Random();
    private final HashMap<String, Integer> donateHashMap = new HashMap<>();

    Picbot(DefaultBotOptions botOptions) {
        super(botOptions);

        File donateListFile = new File("donateList.txt");
        if (donateListFile.exists()) {
            try (BufferedReader fileReader = new BufferedReader(new FileReader("donateList.txt"))){
                fileReader.lines().forEach(s -> {
                    String[] splitted = s.split("\\s+");
                    donateHashMap.put(splitted[0], Integer.parseInt(splitted[1]));
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                if(!donateListFile.createNewFile())
                    throw new IOException("donateList.txt file not created");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getBotUsername() {
        return System.getenv("BOT_USERNAME");
    }

    @Override
    public String getBotToken(){
        return System.getenv("BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        logger.info("Get last update: " + update);
        System.out.println("Get last update: " + update);

        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            if (update.getMessage().getSuccessfulPayment().getInvoicePayload().equals("donate")) {
                synchronized(this) {
                    if (donateHashMap.containsKey(update.getMessage().getChat().getUserName())) {
                        donateHashMap.put(update.getMessage().getChat().getUserName(),
                                donateHashMap.get(update.getMessage().getChat().getUserName()) +
                                        update.getMessage().getSuccessfulPayment().getTotalAmount());

                    } else donateHashMap.put(update.getMessage().getChat().getUserName(),
                            update.getMessage().getSuccessfulPayment().getTotalAmount());

                    donateHashMap.forEach((k, v) -> {
                        try (BufferedWriter fileWriter = new BufferedWriter(
                                new FileWriter("donateList.txt", true))){
                            fileWriter.write(k + " " + v);
                            fileWriter.newLine();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        }

        if (update.hasPreCheckoutQuery()) {
            if (update.getPreCheckoutQuery().getInvoicePayload().equals("donate")) {
                AnswerPreCheckoutQuery answerPreCheckoutQuery = new AnswerPreCheckoutQuery()
                        .setPreCheckoutQueryId(update.getPreCheckoutQuery().getId())
                        .setOk(true);
                try {
                    execute(answerPreCheckoutQuery);
                } catch(TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }

        if (update.hasCallbackQuery()) {
            if (update.getCallbackQuery().getMessage().getReplyMarkup().getKeyboard()
                    .get(0).get(0).getCallbackData().equals("likePhoto")) {

                numberOfLikedPictures.incrementAndGet();

                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery()
                        .setCallbackQueryId(update.getCallbackQuery().getId());
                try {
                    execute(answerCallbackQuery);
                } catch(TelegramApiException e) {
                    e.printStackTrace();
                }

                EditMessageReplyMarkup editReplyMarkup = new EditMessageReplyMarkup()
                        .setMessageId(update.getCallbackQuery().getMessage().getMessageId())
                        .setChatId(update.getCallbackQuery().getMessage().getChatId())
                        .setReplyMarkup(null);
                try {
                    execute(editReplyMarkup);
                } catch(TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            Message receivedMessage = update.getMessage();
            switch (receivedMessage.getText()){
                case "/start": {
                    LeaveChat leaveChat = new LeaveChat().setChatId(receivedMessage.getChatId());
                    try {
                        execute(leaveChat);
                    } catch(TelegramApiException e) {
                        e.printStackTrace();
                    }
                    sendMessage(receivedMessage.getChatId(), "Привет! Я умею присылать случайные картинки с Unsplash.com. Нажимай на кнопку ✨magic✨ - отправлю тебе картинку.");
                    break;
                }
                case "/help": {
                    sendMessage(receivedMessage.getChatId(),
                            "Все очень просто! Нажми на кнопку ✨magic✨ и я пришлю тебе случайную картинку.\n" +
                                    "Доступные команды:\n /getpic\n /info\n /donate\n /donatelist");
                    break;
                }
                case "/info": {
                    float likestat = (float)numberOfLikedPictures.get()/(float) numberOfSendPictures.get()*100;
                    sendMessage(receivedMessage.getChatId(),
                            "Бот бесперебойно работает " +
                                    SECONDS.between(dateTimeWork, LocalDateTime.now()) + " секунд\n" +
                                    "Всего картинок отправлено: " + numberOfSendPictures + "\n" +
                                    "Понравившихся картинок " + String.format("%.2f", likestat) + "%\n\n" +
                                    "Бот написан 12 декабря 2019\n" +
                                    "Автор бота - Дмитрий Погребной, студент 3 курса ПИ СПбГУ\n" +
                                    "Сказать спасибо автору - /donate\n" +
                                    "Tg: @pogrebnoy\nGithub: https://github.com/DmitryPogrebnoy");

                    break;
                }
                case "/donate": {
                    SendInvoice sendInvoice = new SendInvoice()
                            .setChatId((receivedMessage.getChatId()).intValue())
                            .setTitle("Сказать спасибо автору бота\uD83D\uDCB0")
                            .setDescription("Твой username попадет в список спонсоров /donatelist")
                            .setPayload("donate")
                            .setProviderToken(System.getenv("PAYMENT_PROVIDER_TOKEN"))
                            .setStartParameter("start_parameter")
                            .setCurrency("RUB");
                    LabeledPrice labelPriceSimpleThanks = new LabeledPrice();
                    labelPriceSimpleThanks.setLabel("Простое человеческое спасибо\uD83D\uDCB0");
                    labelPriceSimpleThanks.setAmount(6356);
                    ArrayList<LabeledPrice> labeledPrice = new ArrayList<>();
                    labeledPrice.add(labelPriceSimpleThanks);
                    sendInvoice.setPrices(labeledPrice);

                    try {
                        execute(sendInvoice);
                    } catch(TelegramApiException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case "/donatelist": {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("Спонсоры проекта\uD83D\uDCB8\n");
                    synchronized(this) {
                        donateHashMap.entrySet().stream()
                                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                                .forEachOrdered( entry -> stringBuilder
                                        .append(entry.getKey()).append(" - ").append((float)entry.getValue()/100).append(" руб"));
                    }
                    sendMessage(receivedMessage.getChatId(), stringBuilder.toString());
                    break;
                }
                case "/getpic":
                case "✨magic✨": {
                    SendPhoto sendPhotoRequest = new SendPhoto();
                    sendPhotoRequest.setChatId(update.getMessage().getChatId());
                    sendPhotoRequest.setPhoto("https://source.unsplash.com/random?sig=" + random.nextLong());

                    InlineKeyboardMarkup keyboardInline = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();
                    List<InlineKeyboardButton> keyboardRow = new ArrayList<>();
                    keyboardRow.add(new InlineKeyboardButton().setText("❤").setCallbackData("likePhoto"));
                    keyboardRows.add(keyboardRow);
                    keyboardInline.setKeyboard(keyboardRows);
                    sendPhotoRequest.setReplyMarkup(keyboardInline);

                    try {
                        execute(sendPhotoRequest);
                        numberOfSendPictures.incrementAndGet();
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                default:
                {
                    SendSticker sticker = new SendSticker()
                            .setChatId(receivedMessage.getChatId())
                            .setSticker("CAADAgADGQEAAve-rALM9Um0U0MTixYE");
                    try{
                        execute(sticker);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private ReplyKeyboardMarkup getReplyKeyboardMarkup(){
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("✨magic✨");
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        return keyboardMarkup;
    }

    private void sendMessage(Long chatId, String message) {
        SendMessage sendMessage = new SendMessage()
                .setChatId(chatId)
                .setText(message)
                .setReplyMarkup(getReplyKeyboardMarkup());
        try {
            Message sendedMessage = execute(sendMessage);
            logger.info(sendedMessage.toString());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
