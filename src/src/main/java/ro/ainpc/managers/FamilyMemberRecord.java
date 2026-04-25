package ro.ainpc.managers;

public record FamilyMemberRecord(
    String name,
    String relationType,
    boolean alive,
    Integer relatedNpcId
) {
}
