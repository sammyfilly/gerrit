// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.audit;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import java.sql.Timestamp;
import java.util.Collection;

@ExtensionPoint
public interface GroupMemberAuditListener {

  void onAddAccountsToGroup(
      Account.Id actor, Collection<AccountGroupMember> added, Timestamp addedOn);

  void onDeleteAccountsFromGroup(
      Account.Id actor, Collection<AccountGroupMember> removed, Timestamp removedOn);

  void onAddGroupsToGroup(Account.Id actor, Collection<AccountGroupById> added, Timestamp addedOn);

  void onDeleteGroupsFromGroup(
      Account.Id actor, Collection<AccountGroupById> deleted, Timestamp removedOn);
}
