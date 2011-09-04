package org.tyrannyofheaven.bukkit.zPermissions.dao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.tyrannyofheaven.bukkit.zPermissions.model.Entry;
import org.tyrannyofheaven.bukkit.zPermissions.model.Membership;
import org.tyrannyofheaven.bukkit.zPermissions.model.PermissionEntity;
import org.tyrannyofheaven.bukkit.zPermissions.model.PermissionWorld;

import com.avaje.ebean.EbeanServer;

public class AvajePermissionDao implements PermissionDao {

    private final EbeanServer ebean;
    
    public AvajePermissionDao(EbeanServer ebean) {
        this.ebean = ebean;
    }

    private EbeanServer getEbeanServer() {
        return ebean;
    }

    private PermissionWorld getWorld(String world, boolean create) {
        PermissionWorld permissionWorld = null;
        if (world != null) {
            permissionWorld = getEbeanServer().find(PermissionWorld.class).where()
                .eq("name", world.toLowerCase())
                .findUnique();
            if (permissionWorld == null) {
                if (create) {
                    permissionWorld = new PermissionWorld();
                    permissionWorld.setName(world.toLowerCase());
                    getEbeanServer().save(permissionWorld);
                }
                else {
                    throw new IllegalArgumentException("No such world");
                }
            }
        }
        return permissionWorld;
    }

    @Override
    public Boolean getPermission(String name, boolean group, String world, String permission) {
        getEbeanServer().beginTransaction();
        try {
            PermissionWorld permissionWorld;
            try {
                permissionWorld = getWorld(world, false);
            }
            catch (IllegalArgumentException e) {
                return null;
            }

            Entry entry = getEbeanServer().find(Entry.class).where()
                .eq("entity.name", name.toLowerCase())
                .eq("entity.group", group)
                .eq("world", permissionWorld)
                .eq("permission", permission.toLowerCase())
                .findUnique();
            if (entry != null)
                return entry.isValue();
            return null;
        }
        finally {
            getEbeanServer().endTransaction();
        }
    }

    @Override
    public void setPermission(String name, boolean group, String world, String permission, boolean value) {
        getEbeanServer().beginTransaction();
        try {
            PermissionWorld permissionWorld = getWorld(world, true);

            PermissionEntity owner = getEbeanServer().find(PermissionEntity.class).where()
                .eq("name", name.toLowerCase())
                .eq("group", group)
                .findUnique();
            if (owner == null) {
                owner = new PermissionEntity();
                owner.setName(name.toLowerCase());
                owner.setGroup(group);
                owner.setDisplayName(name);
            }

            Entry found = null;
            permission = permission.toLowerCase();
            for (Entry entry : owner.getPermissions()) {
                if (entry.getPermission().equals(permission) &&
                        (entry.getWorld() == null ? permissionWorld == null : entry.getWorld().equals(permissionWorld))) {
                    found = entry;
                    break;
                }
            }
            
            if (found == null) {
                found = new Entry();
                found.setEntity(owner);
                found.setWorld(permissionWorld);
                found.setPermission(permission.toLowerCase());

                owner.getPermissions().add(found);
            }
            
            found.setValue(value);

            getEbeanServer().save(owner);
            getEbeanServer().commitTransaction();
        }
        finally {
            getEbeanServer().endTransaction();
        }
    }

    @Override
    public void unsetPermission(String name, boolean group, String world, String permission) {
        getEbeanServer().beginTransaction();
        try {
            PermissionWorld permissionWorld;
            try {
                permissionWorld = getWorld(world, false);
            }
            catch (IllegalArgumentException e) {
                return;
            }

            Entry entry = getEbeanServer().find(Entry.class).where()
                .eq("entity.name", name.toLowerCase())
                .eq("entity.group", group)
                .eq("world", permissionWorld)
                .eq("permission", permission.toLowerCase())
                .findUnique();
            if (entry != null) {
                getEbeanServer().delete(entry);
                getEbeanServer().commitTransaction();
            }
        }
        finally {
            getEbeanServer().endTransaction();
        }
    }

    @Override
    public void addMember(String groupName, String member) {
        getEbeanServer().beginTransaction();
        try {
            PermissionEntity group = getEbeanServer().find(PermissionEntity.class).where()
                .eq("name", groupName.toLowerCase())
                .eq("group", true)
                .findUnique();

            Membership membership = null;
            if (group == null) {
                group = new PermissionEntity();
                group.setName(groupName.toLowerCase());
                group.setGroup(true);
                group.setDisplayName(groupName);
                getEbeanServer().save(group);
            }
            else {
                membership = getEbeanServer().find(Membership.class).where()
                    .eq("member", member.toLowerCase())
                    .eq("group", group)
                    .findUnique();
            }
            
            if (membership == null) {
                membership = new Membership();
                membership.setMember(member.toLowerCase());
                membership.setGroup(group);
                getEbeanServer().save(membership);
                getEbeanServer().commitTransaction();
            }
        }
        finally {
            getEbeanServer().endTransaction();
        }
    }

    @Override
    public void removeMember(String groupName, String member) {
        getEbeanServer().beginTransaction();
        try {
            PermissionEntity group = getEbeanServer().find(PermissionEntity.class).where()
                .eq("name", groupName.toLowerCase())
                .eq("group", true)
                .findUnique();
            
            if (group != null) {
                Membership membership = getEbeanServer().find(Membership.class).where()
                    .eq("member", member.toLowerCase())
                    .eq("group", group)
                    .findUnique();
                
                if (membership != null) {
                    getEbeanServer().delete(membership);
                    getEbeanServer().commitTransaction();
                }
            }
        }
        finally {
            getEbeanServer().endTransaction();
        }
    }

    @Override
    public Set<PermissionEntity> getGroups(String member) {
        // NB: relies on outer transaction
        if (getEbeanServer().currentTransaction() == null)
            throw new IllegalStateException("Needs a transaction");
        List<Membership> memberships = getEbeanServer().find(Membership.class).where()
            .eq("member", member.toLowerCase())
            .findList();

        Set<PermissionEntity> groups = new HashSet<PermissionEntity>();
        for (Membership membership : memberships) {
            groups.add(membership.getGroup());
        }
        return groups;
    }

    @Override
    public PermissionEntity getEntity(String name, boolean group) {
        // NB: relies on outer transaction
        if (getEbeanServer().currentTransaction() == null)
            throw new IllegalStateException("Needs a transaction");
        return getEbeanServer().find(PermissionEntity.class).where()
            .eq("name", name.toLowerCase())
            .eq("group", group)
            .findUnique();
    }

    @Override
    public void setGroup(String playerName, String groupName) {
        getEbeanServer().beginTransaction();
        try {
            PermissionEntity group = getEbeanServer().find(PermissionEntity.class).where()
                .eq("name", groupName.toLowerCase())
                .eq("group", true)
                .findUnique();

            if (group == null) {
                group = new PermissionEntity();
                group.setName(groupName.toLowerCase());
                group.setGroup(true);
                group.setDisplayName(groupName);
                getEbeanServer().save(group);
            }

            List<Membership> memberships = getEbeanServer().find(Membership.class).where()
                .eq("member", playerName.toLowerCase())
                .findList();

            Membership found = null;
            List<Membership> toDelete = new ArrayList<Membership>();
            for (Membership membership : memberships) {
                if (!membership.getGroup().equals(group)) {
                    toDelete.add(membership);
                }
                else {
                    found = membership;
                }
            }
            getEbeanServer().delete(toDelete);
            
            if (found == null) {
                found = new Membership();
                found.setMember(playerName.toLowerCase());
                found.setGroup(group);
                getEbeanServer().save(found);
            }
            
            getEbeanServer().commitTransaction();
        }
        finally {
            getEbeanServer().endTransaction();
        }
    }

    @Override
    public List<PermissionEntity> getEntities(boolean group) {
        getEbeanServer().beginTransaction();
        try {
            return getEbeanServer().find(PermissionEntity.class).where()
                .eq("group", group)
                .findList();
        }
        finally {
            getEbeanServer().endTransaction();
        }
    }

}
