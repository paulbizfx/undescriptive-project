package com.github.javadev.undescriptive.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.github.javadev.undescriptive.protocol.request.*;
import com.github.javadev.undescriptive.protocol.response.*;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.Realm;
import com.ning.http.client.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AsyncClient {
    private static final String BASE_URL = "http://www.dragonsofmugloar.com";
    private static final String WEATHER_URL = "/weather/api/report/";

    private final static ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JodaModule())
        .registerModule(new SimpleModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final static XmlMapper XML_MAPPER = new XmlMapper();

    private final AsyncHttpClient httpClient;
    private final String baseUrl;

    private AsyncClient(
            final AsyncHttpClient httpClient,
            final String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    private static AsyncHttpClientConfig commonSetup(final AsyncHttpClientConfig.Builder configBuilder) {
        final Realm realm = new Realm.RealmBuilder().build();
        configBuilder.setRealm(realm);
        return configBuilder.build();
    }

    public static AsyncClient createDefault() {
        return new AsyncClient(
            new AsyncHttpClient(commonSetup(new Builder())), BASE_URL);
    }

    public static AsyncClient create(final AsyncHttpClientConfig config) {
        return new AsyncClient(
            new AsyncHttpClient(commonSetup(new Builder(config))), BASE_URL);
    }

    public void close() {
        this.httpClient.close();
    }

    public void closeAsynchronously() {
        this.httpClient.closeAsynchronously();
    }

    private BoundRequestBuilder get(final String resourceUrl) {
        return this.httpClient.prepareGet(this.baseUrl + resourceUrl);
    }

    private BoundRequestBuilder put(final String resourceUrl, final HasParams hasParams) {
        final BoundRequestBuilder builder = this.httpClient.preparePut(this.baseUrl + resourceUrl);
        final Map<String, Object> params = hasParams.getParams();
        try {
            final String objectAsString = MAPPER.writeValueAsString(params);
            builder.addHeader("Content-Type", "application/json; charset=utf-8");
            builder.setBody(objectAsString); 
        } catch (Exception ignore) {            
        }
        return builder;
    }

    public ListenableFuture<GameResponse> getGame() {
        return execute(GameResponse.class, get("/api/game"));
    }

    public ListenableFuture<SolutionResponse> putGame(Integer id, SolutionRequest solutionRequest) {
        return execute(SolutionResponse.class, put("/api/game/" + id + "/solution", solutionRequest));
    }

    public SolutionRequest solveGame(GameResponseItem gameResponseItem) {
        final List<Integer> knightAttrs = Arrays.asList(gameResponseItem.getAttack(),
            gameResponseItem.getArmor(), gameResponseItem.getAgility(), gameResponseItem.getEndurance());
        final Integer[] indexes = { 0, 1, 2, 3 };
        
        Arrays.sort(indexes, new Comparator<Integer>() {
            @Override public int compare(final Integer o1, final Integer o2) {
                return Integer.valueOf(knightAttrs.get(o1)).compareTo(Integer.valueOf(knightAttrs.get(o2)));
            }
        });
        int maxIndex = indexes[3];
        int secondMaxIndex = indexes[2];
        int thirdMaxIndex = indexes[1];
        int forthMaxIndex = indexes[0];
        int maxItem = Collections.max(knightAttrs);
        int countMax = 0;
        int maxIndex1 = 0;
        int maxIndex2 = 0;
        int index = 0;
        for (Integer attr : knightAttrs) {
            if (attr.equals(maxItem)) {
                countMax += 1;
                if (countMax == 2) {
                    maxIndex1 = maxIndex;
                    maxIndex2 = index;
                }
            }
            index += 1;
        }
        int[] dragonAttrs = new int[] {0, 0, 0, 0};
        if (countMax == 1) {
            dragonAttrs[maxIndex] = 10;
            dragonAttrs[secondMaxIndex] = 5;
            dragonAttrs[thirdMaxIndex] = 4;
            dragonAttrs[forthMaxIndex] = 1;
        }
        if (countMax == 2) {
            dragonAttrs[secondMaxIndex] = 10;
            dragonAttrs[maxIndex1] = 5;
            dragonAttrs[maxIndex2] = 4;
            dragonAttrs[thirdMaxIndex] = 1;
        } else {
            dragonAttrs[maxIndex] = 10;
            dragonAttrs[secondMaxIndex] = 4;
            dragonAttrs[thirdMaxIndex] = 4;
            dragonAttrs[forthMaxIndex] = 2;
        }
            System.out.println("maxIndex - " + maxIndex);
            System.out.println("secondMaxIndex - " + secondMaxIndex);
            System.out.println("thirdMaxIndex - " + thirdMaxIndex);
            System.out.println("forthMaxIndex - " + forthMaxIndex);
        final SolutionRequest request = SolutionRequest.builder()
            .scale(dragonAttrs[0])
            .claw(dragonAttrs[1])
            .wing(dragonAttrs[2])
            .fire(dragonAttrs[3])
            .build();
        return request;
    }

    public ListenableFuture<WeatherResponse> getWeather(Integer id) {
        return execute(WeatherResponse.class, get(WEATHER_URL + id));
    }

    private static <T> ListenableFuture<T> execute(
            final Class<T> clazz,
            final BoundRequestBuilder request) {
        final SettableFuture<T> guavaFut = SettableFuture.create();
        try {
            request.execute(new GuavaFutureConverter<T>(clazz, guavaFut));
        }
        catch (final IOException e) {
            guavaFut.setException(e);
        }
        return guavaFut;
    }

    private static class GuavaFutureConverter<T> extends AsyncCompletionHandler<T> {
        final Class<T> clazz;
        final SettableFuture<T> guavaFut;

        public GuavaFutureConverter(
                final Class<T> clazz,
                final SettableFuture<T> guavaFut) {
            this.clazz = clazz;
            this.guavaFut = guavaFut;
        }

        private static boolean isSuccess(final Response response) {
            final int statusCode = response.getStatusCode();
            return (statusCode > 199 && statusCode < 400);
        }

        @Override
        public void onThrowable(final Throwable t) {
            guavaFut.setException(t);
        }

        @Override
        public T onCompleted(final Response response) throws Exception {
            if (isSuccess(response)) {
                final T value = clazz == WeatherResponse.class ? XML_MAPPER.readValue(response.getResponseBody(), clazz)
                    : MAPPER.readValue(response.getResponseBody(), clazz);
                guavaFut.set(value);
                return value;
            } else {
                throw new UnsupportedOperationException(response.getResponseBody());
            }
        }
    }
}
