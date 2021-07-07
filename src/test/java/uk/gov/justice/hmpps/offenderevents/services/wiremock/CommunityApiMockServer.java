package uk.gov.justice.hmpps.offenderevents.services.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.intellij.lang.annotations.Language;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

public class CommunityApiMockServer extends WireMockServer {
    CommunityApiMockServer() {
        super(8087);
    }

    public void stubForRecall(String nomsNumber) {
        stubFor(
            get(String.format("/secure/offenders/nomsNumber/%s/convictions/active/nsis/recall", nomsNumber)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(recall())
                    .withStatus(200)
            )
        );
    }

    @Language("JSON")
    private String recall() {
        return """
            {
                "nsis": [
                    {
                        "nsiId": 2500032231,
                        "nsiType": {
                            "code": "REC",
                            "description": "Request for Recall"
                        },
                        "nsiSubType": {
                            "code": "REC02",
                            "description": "Emergency"
                        },
                        "nsiStatus": {
                            "code": "REC01",
                            "description": "Recall Initiated"
                        },
                        "statusDateTime": "2021-05-13T12:42:00",
                        "referralDate": "2021-05-12",
                        "lengthUnit": "Months",
                        "nsiManagers": [
                            {
                                "probationArea": {
                                    "probationAreaId": 1500001006,
                                    "code": "N07",
                                    "description": "NPS London",
                                    "organisation": {
                                        "code": "NPS",
                                        "description": "National Probation Service"
                                    }
                                },
                                "team": {
                                    "code": "N07UAT",
                                    "description": "Unallocated Team(N07)",
                                    "localDeliveryUnit": {
                                        "code": "N07NPSA",
                                        "description": "N07 Division"
                                    },
                                    "teamType": {
                                        "code": "N07NPS1",
                                        "description": "N07 LDU 1"
                                    },
                                    "district": {
                                        "code": "N07NPSA",
                                        "description": "N07 Division"
                                    },
                                    "borough": {
                                        "code": "N07100",
                                        "description": "N07 Cluster 1"
                                    }
                                },
                                "staff": {
                                    "staffCode": "N07UATU",
                                    "staffIdentifier": 1500543296,
                                    "staff": {
                                        "forenames": "Unallocated Staff(N07)",
                                        "surname": "Staff"
                                    },
                                    "teams": [
                                        {
                                            "code": "N07UAT",
                                            "description": "Unallocated Team(N07)",
                                            "localDeliveryUnit": {
                                                "code": "N07NPSA",
                                                "description": "N07 Division"
                                            },
                                            "teamType": {
                                                "code": "N07NPS1",
                                                "description": "N07 LDU 1"
                                            },
                                            "district": {
                                                "code": "N07NPSA",
                                                "description": "N07 Division"
                                            },
                                            "borough": {
                                                "code": "N07100",
                                                "description": "N07 Cluster 1"
                                            }
                                        }
                                    ]
                                },
                                "startDate": "2021-05-12"
                            }
                        ],
                        "intendedProvider": {
                            "probationAreaId": 1500001006,
                            "code": "N07",
                            "description": "NPS London",
                            "organisation": {
                                "code": "NPS",
                                "description": "National Probation Service"
                            }
                        },
                        "active": true,
                        "recallRejectedOrWithdrawn": true,
                        "outcomeRecall": false
                    },
                    {
                        "nsiId": 2500032227,
                        "nsiType": {
                            "code": "REC",
                            "description": "Request for Recall"
                        },
                        "nsiSubType": {
                            "code": "REC02",
                            "description": "Emergency"
                        },
                        "nsiOutcome": {
                            "code": "REC03",
                            "description": "Recall Rejected by NPS"
                        },
                        "nsiStatus": {
                            "code": "REC08",
                            "description": "Recall Submitted to PPCS"
                        },
                        "statusDateTime": "2021-05-13T12:22:00",
                        "referralDate": "2021-05-13",
                        "lengthUnit": "Months",
                        "nsiManagers": [
                            {
                                "probationArea": {
                                    "probationAreaId": 1500001006,
                                    "code": "N07",
                                    "description": "NPS London",
                                    "organisation": {
                                        "code": "NPS",
                                        "description": "National Probation Service"
                                    }
                                },
                                "team": {
                                    "code": "N07UAT",
                                    "description": "Unallocated Team(N07)",
                                    "localDeliveryUnit": {
                                        "code": "N07NPSA",
                                        "description": "N07 Division"
                                    },
                                    "teamType": {
                                        "code": "N07NPS1",
                                        "description": "N07 LDU 1"
                                    },
                                    "district": {
                                        "code": "N07NPSA",
                                        "description": "N07 Division"
                                    },
                                    "borough": {
                                        "code": "N07100",
                                        "description": "N07 Cluster 1"
                                    }
                                },
                                "staff": {
                                    "staffCode": "N07UATU",
                                    "staffIdentifier": 1500543296,
                                    "staff": {
                                        "forenames": "Unallocated Staff(N07)",
                                        "surname": "Staff"
                                    },
                                    "teams": [
                                        {
                                            "code": "N07UAT",
                                            "description": "Unallocated Team(N07)",
                                            "localDeliveryUnit": {
                                                "code": "N07NPSA",
                                                "description": "N07 Division"
                                            },
                                            "teamType": {
                                                "code": "N07NPS1",
                                                "description": "N07 LDU 1"
                                            },
                                            "district": {
                                                "code": "N07NPSA",
                                                "description": "N07 Division"
                                            },
                                            "borough": {
                                                "code": "N07100",
                                                "description": "N07 Cluster 1"
                                            }
                                        }
                                    ]
                                },
                                "startDate": "2021-05-13"
                            }
                        ],
                        "intendedProvider": {
                            "probationAreaId": 1500001006,
                            "code": "N07",
                            "description": "NPS London",
                            "organisation": {
                                "code": "NPS",
                                "description": "National Probation Service"
                            }
                        },
                        "active": false,
                        "recallRejectedOrWithdrawn": false
                    }
                ]
            }
                        """;
    }

    public void stubForRecallNotFound(String nomsNumber) {
        stubFor(
            get(String.format("/secure/offenders/nomsNumber/%s/convictions/active/nsis/recall", nomsNumber)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                           "status": 404
                        }
                        """)
                    .withStatus(404)
            )
        );

    }

    public void stubHealthPing(Integer status) {
        stubFor(
            WireMock.get("/health/ping").willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody((status == 200) ? "pong" : "some error")
                    .withStatus(status)));
    }
}
