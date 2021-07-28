/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {PluginApi} from '../../../api/plugin';
import {HookApi, HookCallback, PluginElement} from '../../../api/hook';

export class GrDomHooksManager {
  private hooks: Record<string, GrDomHook<PluginElement>>;

  private plugin: PluginApi;

  constructor(plugin: PluginApi) {
    this.plugin = plugin;
    this.hooks = {};
  }

  _getHookName(endpointName: string, moduleName?: string) {
    if (moduleName) {
      return endpointName + ' ' + moduleName;
    } else {
      // lowercase in case plugin's name contains uppercase letters
      // TODO: this still can not prevent if plugin has invalid char
      // other than uppercase, but is the first step
      // https://html.spec.whatwg.org/multipage/custom-elements.html#valid-custom-element-name
      const pluginName: string = this.plugin.getPluginName() || 'unknownplugin';
      return pluginName.toLowerCase() + '-autogenerated-' + endpointName;
    }
  }

  getDomHook<T extends PluginElement>(
    endpointName: string,
    moduleName?: string
  ): HookApi<T> {
    const hookName = this._getHookName(endpointName, moduleName);
    if (!this.hooks[hookName]) {
      this.hooks[hookName] = (new GrDomHook<T>(
        hookName,
        moduleName
      ) as unknown) as GrDomHook<PluginElement>;
    }
    return (this.hooks[hookName] as unknown) as GrDomHook<T>;
  }
}

export class GrDomHook<T extends PluginElement> implements HookApi<T> {
  private instances: HTMLElement[] = [];

  private attachCallbacks: HookCallback<T>[] = [];

  private detachCallbacks: HookCallback<T>[] = [];

  /**
   * The name of the (custom) element that is going to be created. Matches the T
   * type parameter.
   */
  private readonly moduleName: string;

  private lastAttachedPromise: Promise<HTMLElement> | null = null;

  constructor(hookName: string, moduleName?: string) {
    if (moduleName) {
      this.moduleName = moduleName;
    } else {
      this.moduleName = hookName;
      this._createPlaceholder(hookName);
    }
  }

  _createPlaceholder(hookName: string) {
    class HookPlaceholder extends PolymerElement {
      static get is() {
        return hookName;
      }

      static get properties() {
        return {
          plugin: Object,
          content: Object,
        };
      }
    }

    customElements.define(HookPlaceholder.is, HookPlaceholder);
  }

  handleInstanceDetached(instance: T) {
    const index = this.instances.indexOf(instance);
    if (index !== -1) {
      this.instances.splice(index, 1);
    }
    this.detachCallbacks.forEach(callback => callback(instance));
  }

  handleInstanceAttached(instance: T) {
    this.instances.push(instance);
    this.attachCallbacks.forEach(callback => callback(instance));
  }

  /**
   * Get instance of last DOM hook element attached into the endpoint.
   * Returns a Promise, that's resolved when attachment is done.
   */
  getLastAttached(): Promise<HTMLElement> {
    if (this.instances.length) {
      return Promise.resolve(this.instances.slice(-1)[0]);
    }
    if (!this.lastAttachedPromise) {
      let resolve: HookCallback<T>;
      const promise = new Promise<HTMLElement>(r => {
        resolve = r;
        this.attachCallbacks.push(resolve);
      });
      this.lastAttachedPromise = promise.then((element: HTMLElement) => {
        this.lastAttachedPromise = null;
        const index = this.attachCallbacks.indexOf(resolve);
        if (index !== -1) {
          this.attachCallbacks.splice(index, 1);
        }
        return element;
      });
    }
    return this.lastAttachedPromise;
  }

  /**
   * Get all DOM hook elements.
   */
  getAllAttached() {
    return this.instances;
  }

  /**
   * Install a new callback to invoke when a new instance of DOM hook element
   * is attached.
   */
  onAttached(callback: HookCallback<T>) {
    this.attachCallbacks.push(callback);
    return this;
  }

  /**
   * Install a new callback to invoke when an instance of DOM hook element
   * is detached.
   *
   */
  onDetached(callback: HookCallback<T>) {
    this.detachCallbacks.push(callback);
    return this;
  }

  /**
   * Name of DOM hook element that will be installed into the endpoint.
   */
  getModuleName() {
    return this.moduleName;
  }
}