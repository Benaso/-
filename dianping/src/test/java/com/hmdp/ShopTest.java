package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.hmdp.dto.ShopDoc;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
public class ShopTest {
    @Autowired
    IShopService shopService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;


    private RestHighLevelClient client;

    @Test
    void testInit(){

        System.out.println(client);
    }

    @BeforeEach
    void setUp(){
        this.client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://localhost:9200")
        ));
    }

    @AfterEach
    void tearDown() throws IOException {
        this.client.close();
    }


    @Test
    void testBulk() throws IOException {
        List<Shop> hotels = shopService.list();
        BulkRequest request = new BulkRequest();
        for(Shop hotel : hotels){
            ShopDoc hotelDoc = new ShopDoc(hotel);
            request.add(new IndexRequest("shops")
                    .id(hotelDoc.getId().toString())
                    .source(JSON.toJSONString(hotelDoc),XContentType.JSON)
            );
        }
        client.bulk(request,RequestOptions.DEFAULT);



    }

    @Test
    void loadShopData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
    public static final String SCORE_GEO_KEY = "shop:comments:";
    @Test
    void loadScoreData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SCORE_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            // 3.3.按照评分将顺序导入Redis
//            Collections.sort(value, Comparator.comparingDouble(Shop::getScore));
//            // 3.4.将排序后的店铺数据写入Redis
//            List<String> scoreList = new ArrayList<>();
//            for (Shop shop : value) {
//                scoreList.add(shop.getScore().toString());
//            }
//
//            // Convert the List to an array of strings
//            String[] scoresArray = scoreList.toArray(new String[scoreList.size()]);
//            // 3.5.将排序后的店铺数据写入Redis using StringRedisTemplate
//            stringRedisTemplate.opsForList().leftPushAll(key, scoresArray);
////            List<String> shopTypeCache = scoreList.stream().sorted(Comparator.comparingInt(Shop::getScore))
////                    .map(JSONUtil::toJsonStr)
////                    .collect(Collectors.toList());
//            stringRedisTemplate.opsForList().rightPushAll(key,scoreList);
            List<String> shopTypeCache = value.stream().sorted(Comparator.comparingInt(Shop::getComments))
                    .map(JSONUtil::toJsonStr)
                    .collect(Collectors.toList());
            //此处只能使用右插入，保证顺序
            stringRedisTemplate.opsForList().rightPushAll(key,shopTypeCache);
            stringRedisTemplate.expire(RedisConstants.CACHE_TYPE_KEY,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        }

    }


    /**
     * 将所有数据存储到名为"qq_news"的索引中。
     * @throws IOException
     */
    @Test
    void testBulkRequest() throws IOException {
        // 查询所有的酒店数据
        List<Shop> list = shopService.list();

        // 1.准备Request
        BulkRequest request = new BulkRequest();
        // 2.准备参数
        for (Shop shop : list) {
            // 2.1.转为HotelDoc
            ShopDoc hotelDoc = new ShopDoc(shop);
            // 2.2.转json
            String json = JSON.toJSONString(hotelDoc);
            // 2.3.添加请求
            request.add(new IndexRequest("qq_news").id(shop.getId().toString()).source(json, XContentType.JSON));
        }

        // 3.发送请求
        client.bulk(request, RequestOptions.DEFAULT);
    }

    /**
     * 测试分词效果
     * @throws IOException
     */
    @Test
    void testSuggest() throws IOException {
        //1、准备Request
        SearchRequest request = new SearchRequest("qq_news");
        //2、准备DSL
        request.source()
                .suggest(new SuggestBuilder().addSuggestion(
                        "mySuggestion",
                        SuggestBuilders.completionSuggestion("suggestion")
                                .prefix("M")  //搜索的关键字，这里用prefix,即前置，给方法起名很灵性
                                .skipDuplicates(true)
                                .size(10)
                ));
        //3、发起请求
        SearchResponse response = client.search(request,RequestOptions.DEFAULT);
        //4、解析结果
        Suggest suggest = response.getSuggest();
        //4.1 根据不全查询名称，获取查询结果
        CompletionSuggestion suggestion = suggest.getSuggestion("mySuggestion");
        //4.2 获取options
        List<CompletionSuggestion.Entry.Option> options = suggestion.getOptions();
        //4.3 遍历
        for (CompletionSuggestion.Entry.Option option : options) {
            String text = option.getText().toString();
            System.out.println(text);

        }
    }




//    @AfterEach
//    void tearDown() throws IOException {
//        this.client.close();
//    }
//    public static final String MAPPING_TEMPLATE = "\"mappings\": {\n" +
//            "    \"properties\": {\n" +
//            "      \"id\":{\n" +
//            "        \"type\": \"keyword\"\n" +
//            "      },\n" +
//            "      \"name\":{\n" +
//            "        \"type\": \"text\",\n" +
//            "        \"analyzer\": \"text_anlyzer\",\n" +
//            "        \"search_analyzer\": \"ik_smart\",\n" +
//            "        \"copy_to\": \"all\"\n" +
//            "      },\n" +
//            "      \"typeId\":{\n" +
//            "        \"type\": \"keyword\"\n" +
//            "      },\n" +
//            "      \"images\":{\n" +
//            "        \"type\": \"keyword\",\n" +
//            "        \"index\": false\n" +
//            "      },\n" +
//            "      \"area\":{\n" +
//            "        \"type\": \"keyword\",\n" +
//            "        \"copy_to\": \"all\"\n" +
//            "      },\n" +
//            "      \"address\":{\n" +
//            "        \"type\": \"keyword\",\n" +
//            "        \"index\": false\n" +
//            "      },\n" +
//            "      \"location\":{\n" +
//            "        \"type\": \"geo_point\"\n" +
//            "      },\n" +
//            "      \"avgPrice\":{\n" +
//            "        \"type\": \"integer\"\n" +
//            "      },\n" +
//            "      \"sold\":{\n" +
//            "        \"type\": \"integer\"\n" +
//            "      },\n" +
//            "     \n" +
//            "       \"comments\":{\n" +
//            "        \"type\": \"integer\"\n" +
//            "      },\n" +
//            "       \"score\":{\n" +
//            "        \"type\": \"integer\"\n" +
//            "      },\n" +
//            "      \n" +
//            "       \"openHours\":{\n" +
//            "        \"type\": \"keyword\",\n" +
//            "        \"index\": false\n" +
//            "      },\n" +
//            "      \"createTime\":{\n" +
//            "        \"type\": \"keyword\",\n" +
//            "        \"index\": false\n" +
//            "      },\n" +
//            "     \"updateTime\":{\n" +
//            "        \"type\": \"keyword\",\n" +
//            "        \"index\": false\n" +
//            "      },\n" +
//            "      \"distance\":{\n" +
//            "        \"type\": \"keyword\"\n" +
//            "      },\n" +
//            "       \"all\":{\n" +
//            "        \"type\": \"text\",\n" +
//            "        \"analyzer\": \"text_anlyzer\",\n" +
//            "        \"search_analyzer\": \"ik_smart\"\n" +
//            "      },\n" +
//            "      \"suggestion\":{\n" +
//            "          \"type\": \"completion\",\n" +
//            "          \"analyzer\": \"completion_analyzer\"\n" +
//            "      }\n" +
//            "    }\n" +
//            "  }";




}
