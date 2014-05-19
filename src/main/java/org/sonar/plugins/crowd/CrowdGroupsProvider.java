/*
 * Sonar Crowd Plugin
 * Copyright (C) 2009 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

package org.sonar.plugins.crowd;

import static com.google.common.collect.Collections2.transform;

import com.atlassian.crowd.exception.ApplicationPermissionException;
import com.atlassian.crowd.exception.InvalidAuthenticationException;
import com.atlassian.crowd.exception.OperationFailedException;
import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.crowd.model.group.Group;
import com.atlassian.crowd.service.client.CrowdClient;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sonar.api.security.ExternalGroupsProvider;
import org.sonar.api.utils.SonarException;

import java.util.Collection;
import java.util.List;

/**
 * @author Ferdinand Hübner
 */
public class CrowdGroupsProvider extends ExternalGroupsProvider {

  private static final Logger LOG = LoggerFactory.getLogger(CrowdGroupsProvider.class);
  private static final int PAGING_SIZE = 100; // no idea what a reasonable size might be

  private final CrowdClient crowdClient;

  public CrowdGroupsProvider(CrowdClient crowdClient) {
    this.crowdClient = crowdClient;
  }

  private static final Function<Group, String> GROUP_TO_STRING = new Function<Group, String>() {
    @Override
    public String apply(Group input) {
      return input.getName();
    }
  };

  private Collection<String> getDirectGroupsForUser(String username, int start, int pageSize)
      throws UserNotFoundException, OperationFailedException, InvalidAuthenticationException,
      ApplicationPermissionException {
    return transform(crowdClient.getGroupsForUser(username, start, pageSize), GROUP_TO_STRING);
  }

  private Collection<String> getNestedGroupsForUser(String username, int start, int pageSize)
      throws UserNotFoundException, OperationFailedException, InvalidAuthenticationException,
      ApplicationPermissionException {
    return transform(crowdClient.getGroupsForNestedUser(username, start, pageSize), GROUP_TO_STRING);
  }

  private List<String> getGroupsForUser(String username, boolean nested)
      throws UserNotFoundException,
      OperationFailedException, InvalidAuthenticationException,
      ApplicationPermissionException {

    Builder<String> groups = ImmutableList.builder();
    boolean mightHaveMore = true;
    int groupIndex = 0;
    Collection<String> newGroups;

    while (mightHaveMore) {
      if (nested) {
        newGroups = getDirectGroupsForUser(username, groupIndex, PAGING_SIZE);
      } else {
        newGroups = getNestedGroupsForUser(username, groupIndex, PAGING_SIZE);
      }
      if (newGroups.size() < PAGING_SIZE) {
        mightHaveMore = false;
      }
      groups.addAll(newGroups);
      groupIndex += newGroups.size();
    }
    return groups.build();
  }

  @Override
  public Collection<String> doGetGroups(String username) {
    LOG.debug("Looking up user groups for user {}", username);

    Builder<String> groups = ImmutableList.builder();
    try {
      groups.addAll(getGroupsForUser(username, false));
      groups.addAll(getGroupsForUser(username, true));
      return groups.build();

    } catch (UserNotFoundException e) {
      return null; // API contract for ExternalGroupsProvider
    } catch (OperationFailedException e) {
      throw new SonarException("Unable to retrieve groups for user" + username + " from crowd.", e);
    } catch (ApplicationPermissionException e) {
      throw new SonarException("Unable to retrieve groups for user" + username
          + " from crowd. The application name and password are incorrect.", e);
    } catch (InvalidAuthenticationException e) {
      throw new SonarException(
          "Unable to retrieve groups for user" + username
              + " from crowd. The application is not permitted to perform the "
              + "requested operation on the crowd server.", e);
    }
  }
}