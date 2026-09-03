using Microsoft.EntityFrameworkCore;
using CallRecorderMobileApp.Api.Models;

namespace CallRecorderMobileApp.Api.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

    public DbSet<Device> Devices => Set<Device>();
    public DbSet<CallRecording> CallRecordings => Set<CallRecording>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Device>(e =>
        {
            e.ToTable("DEVICES");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("ID");
            e.Property(x => x.DeviceId).HasColumnName("DEVICE_ID").HasMaxLength(128).IsRequired();
            e.Property(x => x.FcmToken).HasColumnName("FCM_TOKEN").HasMaxLength(512).IsRequired();
            e.Property(x => x.Platform).HasColumnName("PLATFORM").HasMaxLength(32);
            e.Property(x => x.RegisteredAt).HasColumnName("REGISTERED_AT");
            e.Property(x => x.LastSeenAt).HasColumnName("LAST_SEEN_AT");
            e.HasIndex(x => x.DeviceId).IsUnique();
        });

        modelBuilder.Entity<CallRecording>(e =>
        {
            e.ToTable("CALL_RECORDINGS");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("ID");
            e.Property(x => x.DeviceId).HasColumnName("DEVICE_ID").HasMaxLength(128).IsRequired();
            e.Property(x => x.PhoneNumber).HasColumnName("PHONE_NUMBER").HasMaxLength(64).IsRequired();
            e.Property(x => x.Direction).HasColumnName("DIRECTION").HasMaxLength(16);
            e.Property(x => x.StartedAt).HasColumnName("STARTED_AT");
            e.Property(x => x.DurationSeconds).HasColumnName("DURATION_SECONDS");
            e.Property(x => x.FileSizeBytes).HasColumnName("FILE_SIZE_BYTES");
            e.Property(x => x.StoragePath).HasColumnName("STORAGE_PATH").HasMaxLength(1024);
            e.Property(x => x.Status).HasColumnName("STATUS").HasMaxLength(32);
            e.Property(x => x.CreatedAt).HasColumnName("CREATED_AT");
            e.HasIndex(x => x.DeviceId);
        });
    }
}
