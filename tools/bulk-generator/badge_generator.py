import csv
import uuid
import random
from datetime import datetime, timedelta


# Function to generate random timestamps within a range
def random_date(start, end):
    delta = end - start
    random_seconds = int((start + timedelta(seconds=delta.total_seconds() * random.random())).timestamp())
    return datetime.fromtimestamp(random_seconds).strftime('%Y-%m-%dT%H:%M:%SZ')

# Badge headers
headers = ['EMPLOYEE_ID', 'SWIPE_DATE', 'BUILDING_ID', 'BUILDING_ASSIGNED']
start_date = datetime(2020, 1, 1)
end_date = datetime(2023, 12, 31)
filename = 'badge_swipes.csv'
rows_to_generate = 1000000

with open(filename, 'w', newline='') as csvfile:
    writer = csv.writer(csvfile)
    writer.writerow(headers)

    for _ in range(rows_to_generate):
        row = [
            str(uuid.uuid4()),  # UUID as string
            random_date(start_date, end_date),  # Random timestamp
            'BLOCK_' + str(_),  # Sample block data
            'ASSIGNED_' + str(_),  # Sample assigned data
        ]
        writer.writerow(row)

print("CSV generation complete.")
