/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../registry';

export interface FlagsService extends Finalizable {
  isEnabled(experimentId: string): boolean;
  enabledExperiments: string[];
}

/**
 * Experiment ids used in Gerrit.
 */
export enum KnownExperimentId {
  NEW_IMAGE_DIFF_UI = 'UiFeature__new_image_diff_ui',
  CHECKS_DEVELOPER = 'UiFeature__checks_developer',
  BULK_ACTIONS = 'UiFeature__bulk_actions_dashboard',
  DIFF_RENDERING_LIT = 'UiFeature__diff_rendering_lit',
  MORE_FILES_INFO = 'UiFeature__more_files_info',
  PUSH_NOTIFICATIONS = 'UiFeature__push_notifications',
  RELATED_CHANGES_SUBMITTABILITY = 'UiFeature__related_changes_submittability',
  MENTION_USERS = 'UIFeature_mention_users',
}
