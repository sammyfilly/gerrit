// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.restapi.project;

import static com.google.gerrit.entities.RefNames.isConfigRef;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.ProjectApi.ListRefsRequest;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefFilter;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

public class ListBranches implements RestReadView<ProjectResource> {
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final DynamicMap<RestView<BranchResource>> branchViews;
  private final UiActions uiActions;
  private final WebLinks webLinks;

  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage = "maximum number of branches to list")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
      name = "--start",
      aliases = {"-S", "-s"},
      metaVar = "CNT",
      usage = "number of branches to skip")
  public void setStart(int start) {
    this.start = start;
  }

  @Option(
      name = "--match",
      aliases = {"-m"},
      metaVar = "MATCH",
      usage = "match branches substring")
  public void setMatchSubstring(String matchSubstring) {
    this.matchSubstring = matchSubstring;
  }

  @Option(
      name = "--regex",
      aliases = {"-r"},
      metaVar = "REGEX",
      usage = "match branches regex")
  public void setMatchRegex(String matchRegex) {
    this.matchRegex = matchRegex;
  }

  private int limit;
  private int start;
  private String matchSubstring;
  private String matchRegex;

  @Inject
  public ListBranches(
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      DynamicMap<RestView<BranchResource>> branchViews,
      UiActions uiActions,
      WebLinks webLinks) {
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.branchViews = branchViews;
    this.uiActions = uiActions;
    this.webLinks = webLinks;
  }

  public ListBranches request(ListRefsRequest<BranchInfo> request) {
    this.setLimit(request.getLimit());
    this.setStart(request.getStart());
    this.setMatchSubstring(request.getSubstring());
    this.setMatchRegex(request.getRegex());
    return this;
  }

  @Override
  public Response<ImmutableList<BranchInfo>> apply(ProjectResource rsrc)
      throws RestApiException, IOException, PermissionBackendException {
    rsrc.getProjectState().checkStatePermitsRead();

    // Filter on refs/heads/*, substring and regex without checking ref visibility
    List<Ref> allBranches = readAllBranches(rsrc);
    Set<String> targets = getTargets(allBranches);
    ImmutableList<Ref> filtered =
        new RefFilter<>(Constants.R_HEADS, (Ref r) -> r.getName())
            .subString(matchSubstring)
            .regex(matchRegex)
            .filter(allBranches);

    // Filter for visibility, taking 'start' and 'limit' parameters into account
    return Response.ok(
        filterForVisibility(rsrc, filtered, targets).stream()
            .collect(ImmutableList.toImmutableList()));
  }

  BranchInfo toBranchInfo(BranchResource rsrc)
      throws IOException, ResourceNotFoundException, PermissionBackendException {
    try (Repository db = repoManager.openRepository(rsrc.getNameKey())) {
      String refName = rsrc.getRef();
      if (RefNames.isRefsUsersSelf(refName, rsrc.getProjectState().isAllUsers())) {
        refName = RefNames.refsUsers(rsrc.getUser().getAccountId());
      }
      Ref r = db.exactRef(refName);
      if (r == null) {
        throw new ResourceNotFoundException();
      }
      return toBranchInfo(
              r,
              getTargets(ImmutableList.of(r)),
              rsrc.getNameKey(),
              rsrc.getProjectState(),
              rsrc.getUser())
          .get();
    } catch (RepositoryNotFoundException noRepo) {
      throw new ResourceNotFoundException(rsrc.getNameKey().get(), noRepo);
    }
  }

  private List<Ref> readAllBranches(ProjectResource rsrc)
      throws IOException, ResourceNotFoundException {
    List<Ref> refs;
    try (Repository db = repoManager.openRepository(rsrc.getNameKey())) {
      Collection<Ref> heads = db.getRefDatabase().getRefsByPrefix(Constants.R_HEADS);
      refs = new ArrayList<>(heads.size() + 3);
      refs.addAll(heads);
      refs.addAll(
          db.getRefDatabase()
              .exactRef(Constants.HEAD, RefNames.REFS_CONFIG, RefNames.REFS_USERS_DEFAULT)
              .values());
      return refs;
    } catch (RepositoryNotFoundException noGitRepository) {
      throw new ResourceNotFoundException(rsrc.getNameKey().get(), noGitRepository);
    }
  }

  /**
   * Filter the input {@code refs} list w.r.t. current user's visibility of the ref. This also takes
   * into account the {@link #start} and {@link #limit} parameters. We check refs iteratively while
   * keeping track of matching (visible) refs. We only populate the output list if the matching ref
   * ordinal is greater or equal {@link #start} and keep filling the output list until a {@link
   * #limit} number of refs is gotten.
   */
  private List<BranchInfo> filterForVisibility(
      ProjectResource rsrc, List<Ref> refs, Set<String> targets) throws PermissionBackendException {
    refs = refs.stream().sorted(new RefComparator()).collect(Collectors.toUnmodifiableList());
    List<BranchInfo> branches = new ArrayList<>();
    int matchingRefs = 0;
    for (Ref ref : refs) {
      Optional<BranchInfo> info =
          toBranchInfo(ref, targets, rsrc.getNameKey(), rsrc.getProjectState(), rsrc.getUser());
      if (info.isPresent()) {
        matchingRefs += 1;
        if (matchingRefs > start) {
          branches.add(info.get());
        }
        if (limit > 0 && branches.size() == limit) {
          // Break and return earlier if we've already found 'limit' refs. The processing of the
          // remaining refs for visibility is not needed anymore.
          break;
        }
      }
    }
    return branches;
  }

  /**
   * Returns a {@link BranchInfo} if the branch is visible to the caller or {@link Optional#empty()}
   * otherwise.
   */
  private Optional<BranchInfo> toBranchInfo(
      Ref ref,
      Set<String> targets,
      Project.NameKey project,
      ProjectState projectState,
      CurrentUser currentUser)
      throws PermissionBackendException {
    PermissionBackend.ForProject perm = permissionBackend.currentUser().project(project);
    if (ref.isSymbolic()) {
      // A symbolic reference to another branch, instead of
      // showing the resolved value, show the name it references.
      //
      String target = ref.getTarget().getName();

      try {
        perm.ref(target).check(RefPermission.READ);
      } catch (AuthException e) {
        return Optional.empty();
      }

      if (target.startsWith(Constants.R_HEADS)) {
        target = target.substring(Constants.R_HEADS.length());
      }

      BranchInfo info = new BranchInfo();
      info.ref = ref.getName();
      info.revision = target;
      if (!Constants.HEAD.equals(ref.getName())) {
        if (isConfigRef(ref.getName())) {
          // Never allow to delete the meta config branch.
          info.canDelete = null;
        } else {
          info.canDelete =
              perm.ref(ref.getName()).testOrFalse(RefPermission.DELETE)
                      && projectState.statePermitsWrite()
                  ? true
                  : null;
        }
      }
      return Optional.of(info);
    }

    try {
      perm.ref(ref.getName()).check(RefPermission.READ);
      BranchInfo branchInfo =
          createBranchInfo(perm.ref(ref.getName()), ref, projectState, currentUser, targets);
      return Optional.of(branchInfo);
    } catch (AuthException e) {
      // Do nothing.
      return Optional.empty();
    }
  }

  private static Set<String> getTargets(List<Ref> refs) {
    Set<String> targets = Sets.newHashSetWithExpectedSize(1);
    refs.stream().filter(Ref::isSymbolic).forEach(r -> targets.add(r.getTarget().getName()));
    return targets;
  }

  private static class RefComparator implements Comparator<Ref> {
    @Override
    public int compare(Ref a, Ref b) {
      return ComparisonChain.start()
          .compareTrueFirst(isHead(a), isHead(b))
          .compareTrueFirst(isConfig(a), isConfig(b))
          .compare(a.getName(), b.getName())
          .result();
    }

    private static boolean isHead(Ref r) {
      return Constants.HEAD.equals(r.getName());
    }

    private static boolean isConfig(Ref r) {
      return RefNames.REFS_CONFIG.equals(r.getName());
    }
  }

  private BranchInfo createBranchInfo(
      PermissionBackend.ForRef perm,
      Ref ref,
      ProjectState projectState,
      CurrentUser user,
      Set<String> targets) {
    BranchInfo info = new BranchInfo();
    info.ref = ref.getName();
    info.revision = ref.getObjectId() != null ? ref.getObjectId().name() : null;

    if (isConfigRef(ref.getName())) {
      // Never allow to delete the meta config branch.
      info.canDelete = null;
    } else {
      info.canDelete =
          !targets.contains(ref.getName())
                  && perm.testOrFalse(RefPermission.DELETE)
                  && projectState.statePermitsWrite()
              ? true
              : null;
    }

    BranchResource rsrc = new BranchResource(projectState, user, ref);
    for (UiAction.Description d : uiActions.from(branchViews, rsrc)) {
      if (info.actions == null) {
        info.actions = new TreeMap<>();
      }
      info.actions.put(d.getId(), new ActionInfo(d));
    }

    ImmutableList<WebLinkInfo> links =
        webLinks.getBranchLinks(projectState.getName(), ref.getName());
    info.webLinks = links.isEmpty() ? null : links;
    return info;
  }
}
