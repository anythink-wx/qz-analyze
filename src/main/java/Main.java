import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Country;
import org.apache.commons.cli.*;

import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    static DatabaseReader reader;

    static {
        try {
            InputStream resourceAsStream = Main.class.getClassLoader().getResourceAsStream("GeoLite2-City.mmdb");
            reader = new DatabaseReader.Builder(resourceAsStream).withCache(new CHMCache()).build();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final String PATTERN = "(?<ip>\\d+\\.\\d+\\.\\d+\\.\\d+)(\\s-\\s\\[)(?<datetime>[\\s\\S]+)(?<t1>\\][\\s\"]+)(?<request>[A-Z]+) (?<url>[\\S]*) (?<protocol>[\\S]+)[\"] (?<code>\\d+)  [\"](?<referer>[\\S]*)[\"] (?<useragent>[\"][\\S\\s]+[\"])";
    //( - - \[)(?<datetime>[\s\S]+)(?<t1>\][\s"]+)(?<request>[A-Z]+) (?<url>[\S]*) (?<protocol>[\S]+)["] (?<code>\d+) (?<sendbytes>\d+) ["](?<refferer>[\S]*)["] ["](?<useragent>[\S\s]+)["]

    public static void main(String[] args) throws IOException, GeoIp2Exception {
        Option option = new Option("s", "source", true, "日志源文件路径");
        Option option1 = new Option("d", "dist", true, "保存日志位置");
        Options options = new Options();
        options.addOption(option);
        options.addOption(option1);

        CommandLine cli = null;
        CommandLineParser cliParser = new DefaultParser();
        HelpFormatter helpFormatter = new HelpFormatter();

        try{
            cli = cliParser.parse(options,args);

            if(!cli.hasOption("source")){
                throw new Exception("请传递源文件路径");
            }

            String source = cli.getOptionValue("s");
            File file = new File(source);
            if(!file.exists()){
                throw new Exception("源文件不存在:" + source );
            }

            String dist = cli.getOptionValue("dist", "");
            if(dist.equals("")){
                dist = "dist.csv";
            }

            loadLog(source,dist);

            System.out.println("文件输出："+ dist);


        }catch (ParseException e){
            helpFormatter.printHelp("选项错误", options);
        }catch (Exception e){
            System.out.println("错误信息:" + e.getMessage());
        }
    }


    public static String getLocation(String ip) {


        try{
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse cityResponse = reader.city(ipAddress);
            Country country = cityResponse.getCountry();
            return country.getNames().get("zh-CN");

        }catch (Exception e){
            e.printStackTrace();
        }
        return ip;
    }

    public static void loadLog(String source, String dist){
        try (
                BufferedReader bufferedReader = new BufferedReader(new FileReader(source));
                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(dist));
        ) {

            Pattern compile = Pattern.compile(PATTERN);
            String tempString;
            bufferedWriter.write("gameId,loginStart,loginEnd,userId,startGame,downloadGameTime,startLoadGame,loadGameEnd,gameTime,userAgent");
            bufferedWriter.newLine();
            while ((tempString = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(tempString);

                while (matcher.find()){

                    String code = matcher.group("useragent");

                    //System.out.println("code -> " + code);


                    Pattern compile1 = Pattern.compile("(?<=\").*?(?=\")");
                    Matcher matcher1 = compile1.matcher(code);

                    ArrayList<String> strings = new ArrayList<>();
                    while (matcher1.find()){
                        strings.add(matcher1.group());
                    }

                    String useragent = strings.get(0);
                    String ipaddr = strings.get(2);
                    String[] split = ipaddr.split(",");
                    String datetime = matcher.group("datetime");
                    Map<String, String> urlMap = paramToMap(matcher.group("url"));

                    String log = urlMap.get("gameId") + "," + urlMap.get("loginStart") +","+  urlMap.get("loginEnd")
                            +","+ urlMap.get("userId") + ","+ urlMap.get("startGame") +","+ urlMap.get("downloadGameTime")
                            +","+ urlMap.get("startLoadGame") + ","+ urlMap.get("loadGameEnd") +","+ urlMap.get("gameTime")
                            + "," + useragent + "," + split[0] + "," + getLocation(split[0]);

                    bufferedWriter.write(log);
                    bufferedWriter.newLine();
                }
            }
        } catch (Exception e) {
            //System.out.println("error:");
            // System.out.println(e.getCause().getMessage());
            e.printStackTrace();
        }
    }

    public static Map<String, String> paramToMap(String paramStr) {
        String[] params = paramStr.split("&");
        Map<String, String> resMap = new HashMap<>();
        for (int i = 0; i < params.length; i++) {
            String[] param = params[i].split("=");
            if (param.length >= 2) {
                String key = param[0];
                String value = param[1];
                for (int j = 2; j < param.length; j++) {
                    value += "=" + param[j];
                }
                resMap.put(key, value);
            }
        }
        return resMap;
    }
}
