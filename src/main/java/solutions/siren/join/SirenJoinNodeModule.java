/**
 * Copyright (c) 2016, SIREn Solutions. All Rights Reserved.
 *
 * This file is part of the SIREn project.
 *
 * SIREn is a free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * SIREn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package solutions.siren.join;

import org.elasticsearch.common.inject.AbstractModule;
import solutions.siren.join.action.admin.cache.FilterJoinCacheService;
import solutions.siren.join.action.admin.version.IndexVersionService;

public class SirenJoinNodeModule extends AbstractModule {

  private final IndexVersionService indexVersionService;

  public SirenJoinNodeModule(IndexVersionService indexVersionService) {
    this.indexVersionService = indexVersionService;
  }

  @Override
  protected void configure() {
    bind(FilterJoinCacheService.class).asEagerSingleton();
    bind(IndexVersionService.class).toInstance(indexVersionService);
  }

}
