import static com.google.common.truth.Truth.assertWithMessage;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
    PushOneCommit create(PersonIdent i, TestRepository<?> testRepo);
        PersonIdent i, TestRepository<?> testRepo, @Assisted("changeId") String changeId);
    this(notesFactory, approvalsUtil, queryProvider, i, testRepo, SUBJECT, FILE_NAME, FILE_CONTENT);
    this(notesFactory, approvalsUtil, queryProvider, i, testRepo, subject, fileName, content, null);
    this(notesFactory, approvalsUtil, queryProvider, i, testRepo, subject, files, null);
    public ChangeData getChange() {
    public PatchSet getPatchSet() {
    public PatchSet.Id getPatchSetId() {
        Change.Status expectedStatus, String expectedTopic, TestAccount... expectedReviewers) {
        List<TestAccount> expectedCcs) {
      assertReviewers(c, ReviewerStateInternal.REVIEWER, expectedReviewers);
      assertReviewers(c, ReviewerStateInternal.CC, expectedCcs);
        Change c, ReviewerStateInternal state, List<TestAccount> expectedReviewers) {
          approvalsUtil.getReviewers(notesFactory.createChecked(c)).byState(state);
      assertWithMessage(message(refUpdate))
          .that(refUpdate.getStatus())
      assertWithMessage(message(refUpdate)).that(refUpdate.getStatus()).isEqualTo(expectedStatus);