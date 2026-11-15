package com.example.flexsim_worker.client;

import com.example.flexsim_worker.auth.WorkerTokenProvider;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Fetches the DATA FlexSim needs, from the services that own it, with the worker's token.
 *
 *   infeedDetails(exp)   GET experiment-service /api/resourceDetails/getInfeedDetails/{exp}
 *   zoneDetails(exp)     GET experiment-service /api/resourceDetails/getZoneDetails/{exp}
 *   sortPlanDetails(exp) GET experiment-service /api/resourceDetails/getSortPlanDetails/{exp}
 *   parcels(exp)         GET parcel owner        /api/parcels/getParcels/{exp}
 *
 * The answer is passed on to FlexSim EXACTLY as it came (the JSON text), so FlexSim gets the same
 * data as with the old app. The worker does not need to understand it.
 *
 * 401 -> new login and ONE more try. Any other problem is thrown to the caller.
 */
@Component
public class FlexsimDataClient {

    private final RestClient experimentData;
    private final RestClient parcelData;
    private final WorkerTokenProvider tokenProvider;

    public FlexsimDataClient(@Qualifier("experimentDataRestClient") RestClient experimentData,
                             @Qualifier("parcelDataRestClient") RestClient parcelData,
                             WorkerTokenProvider tokenProvider) {
        this.experimentData = experimentData;
        this.parcelData = parcelData;
        this.tokenProvider = tokenProvider;
    }

    public String infeedDetails(long experimentId) {
        return get(experimentData, "/api/resourceDetails/getInfeedDetails/{id}", experimentId);
    }

    public String zoneDetails(long experimentId) {
        return get(experimentData, "/api/resourceDetails/getZoneDetails/{id}", experimentId);
    }

    public String sortPlanDetails(long experimentId) {
        return get(experimentData, "/api/resourceDetails/getSortPlanDetails/{id}", experimentId);
    }

    public String parcels(long experimentId) {
        return get(parcelData, "/api/parcels/getParcels/{id}", experimentId);
    }

    private String get(RestClient client, String uri, long experimentId) {
        Supplier<String> call = () -> client.get()
                .uri(uri, experimentId)
                .accept(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(tokenProvider.getToken()))
                .retrieve()
                .body(String.class);
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized e) {
            tokenProvider.invalidate();
            return call.get();
        }
    }
}
