import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.revwalk.RevBlob;
    @UsedAt(Project.PLUGIN_CODE_OWNERS)
    PushOneCommit create(
        PersonIdent i,
        TestRepository<?> testRepo,
        @Assisted("subject") String subject,
        @Assisted Map<String, String> files,
        @Assisted("changeId") String changeId);

  @AssistedInject
  PushOneCommit(
      @Assisted PersonIdent i,
      @Assisted TestRepository<?> testRepo,
      @Assisted("subject") String subject,
      @Assisted Map<String, String> files,
      @Nullable @Assisted("changeId") String changeId)
  public PushOneCommit addSymlink(String path, String target) throws Exception {
    RevBlob blobId = testRepo.blob(target);
    commitBuilder.edit(
        new PathEdit(path) {
          @Override
          public void apply(DirCacheEntry ent) {
            ent.setFileMode(FileMode.SYMLINK);
            ent.setObjectId(blobId);
          }
        });
    return this;
  }
