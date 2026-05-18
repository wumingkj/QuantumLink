package db

import "quantumlink-server/internal/model"

// CreateContact 添加联系人
func CreateContact(contact *model.Contact) error {
	_, err := DB.Exec(
		`INSERT INTO contacts (user_id, contact_id, added_at) VALUES (?, ?, ?)`,
		contact.UserID, contact.ContactID, contact.AddedAt,
	)
	return err
}

// DeleteContact 删除联系人
func DeleteContact(userID, contactID string) error {
	_, err := DB.Exec(
		`DELETE FROM contacts WHERE user_id = ? AND contact_id = ?`,
		userID, contactID,
	)
	return err
}
