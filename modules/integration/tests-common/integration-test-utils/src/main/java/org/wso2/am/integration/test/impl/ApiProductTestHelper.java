/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.am.integration.test.impl;

import org.testng.Assert;
import org.wso2.am.integration.clients.publisher.api.ApiException;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIDTO;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIOperationsDTO;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIProductBusinessInformationDTO;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIProductDTO;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIProductInfoDTO;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIProductListDTO;
import org.wso2.am.integration.clients.publisher.api.v1.dto.ProductAPIDTO;
import org.wso2.am.integration.clients.publisher.api.v1.dto.ScopeDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.APIBusinessInformationDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.APIInfoDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.APIListDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.APITiersDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ScopeInfoDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * A collection of helper methods to aid in setting up and testing APIProducts
 */
public class ApiProductTestHelper {
    private RestAPIPublisherImpl restAPIPublisher;
    private RestAPIStoreImpl restAPIStore;

    public ApiProductTestHelper(RestAPIPublisherImpl restAPIPublisher, RestAPIStoreImpl restAPIStore) {
        this.restAPIPublisher = restAPIPublisher;
        this.restAPIStore = restAPIStore;
    }

    public APIProductDTO createAPIProductInPublisher(String provider, String name, String context,
                                                     List<APIDTO> apisToBeUsed, List<String> policies)
            throws ApiException {
        // Select resources from APIs to be used by APIProduct
        List<ProductAPIDTO> resourcesForProduct = getResourcesForProduct(apisToBeUsed, 2);

        // Generate APIProductDTO
        APIProductDTO apiProductDTO = DtoFactory.createApiProductDTO(provider, name, context,
                resourcesForProduct, policies);

        // Create APIProduct and validate response code
        APIProductDTO responseData = restAPIPublisher.addApiProduct(apiProductDTO);

        // Validate that APIProduct resources returned in response data are same as originally selected API resources
        List<ProductAPIDTO> responseResources = responseData.getApis();
        Assert.assertEquals(new HashSet<>(responseResources), new HashSet<>(resourcesForProduct));

        // Validate mandatory fields returned in response data
        Assert.assertEquals(responseData.getProvider(), provider);
        Assert.assertEquals(responseData.getName(), name);
        Assert.assertEquals(responseData.getContext(), context);


        return responseData;
    }

    public void verfiyApiProductInPublisher(APIProductDTO responseData) throws ApiException {
        // Validate APIProduct in publisher listing
        APIProductListDTO apiProductsList = restAPIPublisher.getAllApiProducts();

        List<APIProductInfoDTO> apiProducts = apiProductsList.getList();

        boolean isAPIProductInListing = false;
        int productCount = 0;
        for (APIProductInfoDTO apiProduct : apiProducts) {
            if (apiProduct.getId().equals(responseData.getId())) {
                isAPIProductInListing = true;
                ++productCount;
                verifyApiProductInfoWithApiProductDto(apiProduct, responseData);
            }
        }

        Assert.assertTrue(isAPIProductInListing);
        Assert.assertEquals(productCount, 1);

        // Validate APIProduct by Id
        APIProductDTO returnedProduct = restAPIPublisher.getApiProduct(responseData.getId());

        Assert.assertEquals(returnedProduct, responseData);
    }

    public org.wso2.am.integration.clients.store.api.v1.dto.APIDTO verifyApiProductInPortal(APIProductDTO apiProductDTO)
            throws org.wso2.am.integration.clients.store.api.ApiException {
        org.wso2.am.integration.clients.store.api.v1.dto.APIDTO responseData = restAPIStore.getAPI(apiProductDTO.getId());

        // Validate mandatory fields returned in response data
        verifyApiDtoWithApiProduct(responseData, apiProductDTO);

        APIListDTO apiList = restAPIStore.getAllAPIs();

        List<APIInfoDTO> apiInfos = apiList.getList();

        boolean isAPIProductInListing = false;
        int productCount = 0;
        for (APIInfoDTO apiInfo : apiInfos) {
            if (apiInfo.getId().equals(apiProductDTO.getId())) {
                isAPIProductInListing = true;
                ++productCount;
                verifyApiInfoDtoWithApiProduct(apiInfo, apiProductDTO);
            }
        }

        Assert.assertTrue(isAPIProductInListing);
        Assert.assertEquals(productCount, 1);

        return responseData;
    }

    /**
     * Returns a collection of API resources which can be used by an APIProduct,
     * by selecting a given number of resources from each available API provided
     *
     * @param apiDTOs API List
     * @param numberOfOperations Number of resources to select from a given API
     * @return Collection of API resources to be included in an APIProduct
     */
    private List<ProductAPIDTO> getResourcesForProduct(List<APIDTO> apiDTOs, final int numberOfOperations) {
        Map<APIDTO, Set<APIOperationsDTO>> selectedApiResourceMapping = new HashMap<>();

        // Pick two operations from each API to be used to create the APIProduct.
        for (APIDTO apiDto : apiDTOs) {
            selectOperationsFromAPI(apiDto, numberOfOperations, selectedApiResourceMapping);
        }

        return convertToProductApiResources(selectedApiResourceMapping);
    }

    /**
     * Select a specified number of resources from a given API. Resources will be picked sequentially, where the
     * resources itself will be unordered.
     *
     * @param apiDto API
     * @param numberOfOperations Number of resources to select from the API
     * @param selectedApiResourceMapping Collection for storing the selected resources against the respective API
     */
    private void selectOperationsFromAPI(APIDTO apiDto, final int numberOfOperations,
                                         Map<APIDTO, Set<APIOperationsDTO>> selectedApiResourceMapping) {
        List<APIOperationsDTO> operations = apiDto.getOperations();

        Set<APIOperationsDTO> selectedOperations = new HashSet<>();
        selectedApiResourceMapping.put(apiDto, selectedOperations);

        for (APIOperationsDTO operation : operations) {
            selectedOperations.add(operation);

            // Only select upto the specified number of operations
            if (selectedOperations.size() == numberOfOperations) {
                break;
            }
        }
    }

    /**
     * Converts selected API resources to the APIProduct resource DTO format.
     *
     * @param selectedResources Selected API resources
     * @return Collection of APIProduct resources
     */
    private List<ProductAPIDTO> convertToProductApiResources(Map<APIDTO, Set<APIOperationsDTO>> selectedResources) {
        List<ProductAPIDTO> apiResources = new ArrayList<>();

        for (Map.Entry<APIDTO, Set<APIOperationsDTO>> entry : selectedResources.entrySet()) {
            APIDTO apiDto = entry.getKey();
            Set<APIOperationsDTO> operations = entry.getValue();

            apiResources.add(new ProductAPIDTO().
                    apiId(apiDto.getId()).
                    name(apiDto.getName()).
                    operations(new ArrayList<>(operations)));
        }

        return apiResources;
    }

    private void verifyApiProductInfoWithApiProductDto(APIProductInfoDTO apiProductInfoDTO, APIProductDTO apiProductDTO) {
        Assert.assertEquals(apiProductInfoDTO.getName(), apiProductDTO.getName());
        Assert.assertEquals(apiProductInfoDTO.getProvider(), apiProductDTO.getProvider());
        Assert.assertEquals(apiProductInfoDTO.getContext(), apiProductDTO.getContext());
        Assert.assertEquals(apiProductInfoDTO.getDescription(), apiProductDTO.getDescription());
        Assert.assertEquals(apiProductInfoDTO.isHasThumbnail(), apiProductDTO.isHasThumbnail());
        Assert.assertEquals(new HashSet<>(apiProductInfoDTO.getSecurityScheme()),
                new HashSet<>(apiProductDTO.getSecurityScheme()), "Security Scheme does not match");
        Assert.assertEquals(apiProductInfoDTO.getState().getValue(), apiProductDTO.getState().getValue());
    }

    private void verifyApiDtoWithApiProduct(org.wso2.am.integration.clients.store.api.v1.dto.APIDTO apiDTO, APIProductDTO apiProductDTO) {
        Assert.assertEquals(apiDTO.getId(), apiProductDTO.getId());
        Assert.assertEquals(apiDTO.getAdditionalProperties(), apiProductDTO.getAdditionalProperties());
        verifyBusinessInformation(apiDTO.getBusinessInformation(), apiProductDTO.getBusinessInformation());
        Assert.assertEquals(apiDTO.getContext(), apiProductDTO.getContext());
        Assert.assertEquals(apiDTO.getDescription(), apiProductDTO.getDescription());
        Assert.assertEquals(new HashSet<>(apiDTO.getEnvironmentList()), new HashSet<>(apiProductDTO.getGatewayEnvironments()));
        Assert.assertEquals(apiDTO.getLifeCycleStatus(), apiProductDTO.getState().getValue());
        Assert.assertEquals(apiDTO.getName(), apiProductDTO.getName());
        verifyResources(apiDTO.getOperations(), apiProductDTO.getApis());
        Assert.assertEquals(apiDTO.getProvider(), apiProductDTO.getProvider());
        verifyScopes(apiDTO.getScopes(), apiProductDTO.getScopes());
        Assert.assertEquals(new HashSet<>(apiDTO.getSecurityScheme()), new HashSet<>(apiProductDTO.getSecurityScheme()));
        Assert.assertEquals(new HashSet<>(apiDTO.getTags()), new HashSet<>(apiProductDTO.getTags()));
        verifyPolicies(apiDTO.getTiers(), apiProductDTO.getPolicies());
        Assert.assertEquals(new HashSet<>(apiDTO.getTransport()), new HashSet<>(apiProductDTO.getTransport()));
    }

    private void verifyApiInfoDtoWithApiProduct(APIInfoDTO apiInfo, APIProductDTO apiProductDTO) {
        Assert.assertEquals(apiInfo.getContext(), apiProductDTO.getContext());
        Assert.assertEquals(apiInfo.getDescription(), apiProductDTO.getDescription());
        Assert.assertEquals(apiInfo.getId(), apiProductDTO.getId());
        Assert.assertEquals(apiInfo.getLifeCycleStatus(), apiProductDTO.getState().getValue());
        Assert.assertEquals(apiInfo.getName(), apiProductDTO.getName());
        Assert.assertEquals(apiInfo.getProvider(), apiProductDTO.getProvider());
        Assert.assertEquals(new HashSet<>(apiInfo.getThrottlingPolicies()), new HashSet<>(apiProductDTO.getPolicies()));
    }

    private void verifyBusinessInformation(APIBusinessInformationDTO portalBusinessInfo,
                                           APIProductBusinessInformationDTO publisherBusinessInfo) {
        Assert.assertEquals(portalBusinessInfo.getBusinessOwner(), publisherBusinessInfo.getBusinessOwner());
        Assert.assertEquals(portalBusinessInfo.getBusinessOwnerEmail(), publisherBusinessInfo.getBusinessOwnerEmail());
        Assert.assertEquals(portalBusinessInfo.getTechnicalOwner(), publisherBusinessInfo.getTechnicalOwner());
        Assert.assertEquals(portalBusinessInfo.getTechnicalOwnerEmail(), publisherBusinessInfo.getTechnicalOwnerEmail());
    }

    private void verifyResources(List<org.wso2.am.integration.clients.store.api.v1.dto.APIOperationsDTO> storeAPIOperations,
                                 List<ProductAPIDTO> publisherAPIProductOperations) {
        storeAPIOperations.sort((o1, o2) -> {
            if (o1.getTarget().equals(o2.getTarget())) {
                return o1.getVerb().compareTo(o2.getVerb());
            } else {
                return o1.getTarget().compareTo(o2.getTarget());
            }
        });

        List<APIOperationsDTO> productOperations = new ArrayList<>();
        for (ProductAPIDTO publisherAPIProductOperation : publisherAPIProductOperations) {
            productOperations.addAll(publisherAPIProductOperation.getOperations());
        }

        productOperations.sort((o1, o2) -> {
            if (o1.getTarget().equals(o2.getTarget())) {
                return o1.getVerb().compareTo(o2.getVerb());
            } else {
                return o1.getTarget().compareTo(o2.getTarget());
            }
        });

        Assert.assertEquals(storeAPIOperations.size(), productOperations.size());

        for (int i = 0; i < storeAPIOperations.size(); ++i) {
            org.wso2.am.integration.clients.store.api.v1.dto.APIOperationsDTO apiOperationsDTO = storeAPIOperations.get(i);
            APIOperationsDTO operationsDTO = productOperations.get(i);

            Assert.assertEquals(apiOperationsDTO.getTarget(), operationsDTO.getTarget());
            Assert.assertEquals(apiOperationsDTO.getVerb(), operationsDTO.getVerb());
        }
    }

    private void verifyScopes(List<ScopeInfoDTO> scopeInfoDTOs, List<ScopeDTO> scopeDTOs) {
        scopeInfoDTOs.sort(Comparator.comparing(ScopeInfoDTO::getName));
        scopeDTOs.sort(Comparator.comparing(ScopeDTO::getName));

        Assert.assertEquals(scopeInfoDTOs.size(), scopeDTOs.size());

        for (int i = 0; i < scopeInfoDTOs.size(); ++i) {
            ScopeInfoDTO scopeInfoDTO = scopeInfoDTOs.get(i);
            ScopeDTO scopeDTO = scopeDTOs.get(i);

            Assert.assertEquals(scopeInfoDTO.getDescription(), scopeDTO.getDescription());
            Assert.assertEquals(scopeInfoDTO.getName(), scopeDTO.getName());
            Assert.assertEquals(new HashSet<>(scopeInfoDTO.getRoles()), new HashSet<>(scopeDTO.getBindings().getValues()));
        }

    }

    private void verifyPolicies(List<APITiersDTO> apiTiersDTOs, List<String> policies) {
        Assert.assertEquals(apiTiersDTOs.size(), policies.size());

        apiTiersDTOs.sort(Comparator.comparing(APITiersDTO::getTierName));
        policies.sort(Comparator.naturalOrder());

        for (int i = 0; i < apiTiersDTOs.size(); ++i) {
            APITiersDTO apiTiersDTO = apiTiersDTOs.get(i);
            Assert.assertEquals(apiTiersDTO.getTierName(), policies.get(i));
        }
    }

}
