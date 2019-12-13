package picbot;

import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.ApiContext;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Main {

    public static void main(String[] args) {
        ApiContextInitializer.init();

        DefaultBotOptions botOptions = ApiContext.getInstance(DefaultBotOptions.class);
        /*
          For run app in Russia with proxy
         */
        /*
        botOptions.setProxyHost("50.116.38.201");
        botOptions.setProxyPort(8723);
        botOptions.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);
        */
        botOptions.setMaxThreads(4);

        Picbot bot = new Picbot(botOptions);
        TelegramBotsApi botsApi = new TelegramBotsApi();
        try {
            botsApi.registerBot(bot);
        } catch(TelegramApiException e){
            e.printStackTrace();
        }
    }
}
