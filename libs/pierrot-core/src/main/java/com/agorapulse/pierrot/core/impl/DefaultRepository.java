package com.agorapulse.pierrot.core.impl;

import com.agorapulse.pierrot.core.Content;
import com.agorapulse.pierrot.core.GitHubConfiguration;
import com.agorapulse.pierrot.core.Repository;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHContentBuilder;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.PagedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.EnumSet;
import java.util.Optional;

public class DefaultRepository implements Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRepository.class);
    private static final EnumSet<GHPermissionType> WRITE_PERMISSIONS = EnumSet.of(GHPermissionType.WRITE, GHPermissionType.ADMIN);

    private final GHRepository repository;
    private final GHUser myself;
    private final GitHubConfiguration configuration;

    public DefaultRepository(GHRepository repository, GHUser myself, GitHubConfiguration configuration) {
        this.repository = repository;
        this.myself = myself;
        this.configuration = configuration;
    }

    @Override
    public String getFullName() {
        return repository.getFullName();
    }

    @Override
    public boolean isArchived() {
        return repository.isArchived();
    }

    @Override
    public boolean canWrite() {
        try {
            return WRITE_PERMISSIONS.contains(repository.getPermission(myself));
        } catch (IOException e) {
            LOGGER.info("Exception evaluating permissions for {}", getFullName());
            return false;
        }
    }

    @Override
    public boolean createBranch(String name) {
        if (hasBranch(name)) {
            LOGGER.info("Branch {} already exists in repository {}", name, getFullName());
            return false;
        }

        try {
            repository.createRef("refs/heads/" + name, getLastCommitSha());
            LOGGER.info("Branch {} created in repository {}", name, getFullName());
            return true;
        } catch (IOException e) {
            LOGGER.error("Exception creating branch " + name, e);
            return false;
        }
    }

    @Override
    public Optional<URL> createPullRequest(String branch, String title, String message) {
        try {
            PagedIterator<GHPullRequest> iterator = repository.queryPullRequests().head(branch).base(getDefaultBranch()).list().iterator();
            if (iterator.hasNext()) {
                GHPullRequest existing = iterator.next();
                if (!GHIssueState.OPEN.equals(existing.getState())) {
                    existing.reopen();
                }
                return Optional.of(existing.getHtmlUrl());
            }
            return Optional.of(repository.createPullRequest(title, branch, getDefaultBranch(), message).getHtmlUrl());
        } catch (IOException e) {
            LOGGER.error("Exception creating pull request " + title, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean writeFile(String branch, String message, String path, String text) {
        try {
            GHContentBuilder builder = repository.createContent().branch(branch).path(path).content(text).message(message);
            Optional<Content> existing = getFile(branch, path);
            if (existing.isPresent()) {
                Content content = existing.get();
                if (text.equals(content.getTextContent())) {
                    LOGGER.info("Remote file {} already contains all the changes", path);
                    return false;
                }
                builder.sha(content.getSha());
            }
            builder.commit();
            LOGGER.info("File {} pushed to branch {} of repository {}", path, branch, getFullName());
            return true;
        } catch (IOException e) {
            LOGGER.error("Exception writing file " + path, e);
            return false;
        }
    }

    private Optional<Content> getFile(String branch, String path) {
        try {
            GHContent fileContent = repository.getFileContent(path, branch);
            return Optional.of(new DefaultContent(fileContent, repository, myself, configuration));
        } catch (IOException e) {
            LOGGER.error("Exception fetching file " + path, e);
            return Optional.empty();
        }
    }

    private String getDefaultBranch() {
        return repository.getDefaultBranch() == null ? configuration.getDefaultBranch() : repository.getDefaultBranch();
    }

    private boolean hasBranch(String name) {
        try {
            return repository.getBranches().containsKey(name);
        } catch (IOException e) {
            LOGGER.error("Exception checking presence of branch " + name, e);
            return false;
        }
    }

    private String getLastCommitSha() {
        return repository.listCommits().iterator().next().getSHA1();
    }

}