/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2021 Vladimir Orany.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.agorapulse.pierrot;

import com.agorapulse.pierrot.core.CheckRun;
import com.agorapulse.pierrot.core.GitHubService;
import com.agorapulse.pierrot.mixin.SearchMixin;
import com.agorapulse.pierrot.mixin.StacktraceMixin;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import java.util.Optional;

@Command(
    name = "status",
    description = "searches GitHub Pull Requests and prints their statuses"
)
public class StatusCommand implements Runnable {

    private static final String LINE = "-".repeat(120);
    private static final String DOUBLE_LINE = "=".repeat(120);
    private static final String SUCCESS = "\u2713";
    private static final String FAILURE = "\u2715";
    private static final String PENDING = "\u231B";
    private static final String UNKNOWN = "?";

    @Mixin SearchMixin search;
    @Mixin StacktraceMixin stacktrace;
    @Inject GitHubService service;

    @Override
    public void run() {
        search.searchPullRequests(service, pr -> {
            System.out.println(DOUBLE_LINE);
            System.out.printf("| %s%n", pr.getRepository().getFullName());
            System.out.println(LINE);
            System.out.printf("| %-6s | %s %n", pr.isMerged() ? "MERGED" : "OPEN", pr.getTitle());
            System.out.println(LINE);
            pr.getChecks().forEach(check ->
                System.out.printf(
                    "| %s %s%n",
                    getSymbolFor(check),
                    check.getName()
                )
            );
            return Optional.of(SearchMixin.toSafeUri(pr.getHtmlUrl()));
        });

        System.out.println(DOUBLE_LINE);
        System.out.printf("Found %d pull requests!%n", search.getProcessed());
    }

    private static String getSymbolFor(CheckRun check) {
        if ("completed".equals(check.getStatus())) {
            if ("success".equals(check.getConclusion())) {
                return SUCCESS;
            }
            if ("failure".equals(check.getConclusion())) {
                return FAILURE;
            }
            return UNKNOWN;
        }

        return PENDING;
    }
}