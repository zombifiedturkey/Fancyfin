import type { BaseItemPerson } from "@jellyfin/sdk/lib/generated-client/models/base-item-person";
import { hueFor } from "../lib/hue";
import { imageUrl } from "../lib/images";
import { FadeImage } from "./FadeImage";
import { Shelf } from "./Shelf";

function initials(name: string): string {
	const parts = name.trim().split(/\s+/);
	const first = parts[0]?.[0] ?? "?";
	const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? "") : "";
	return (first + last).toUpperCase();
}

/** One cast member: a photo (or their initials), a name and the role they played. */
export function PersonView({
	name,
	role,
	src,
}: {
	name: string;
	role?: string;
	src?: string;
}) {
	const hue = hueFor(name);
	return (
		<div>
			<div
				className="relative aspect-[2/3] overflow-hidden rounded-card border border-glass-border"
				style={{
					backgroundImage: `linear-gradient(145deg, hsl(${hue} 40% 26%), hsl(${(hue + 50) % 360} 45% 14%))`,
				}}
			>
				<span
					aria-hidden
					className="absolute inset-0 flex items-center justify-center text-2xl font-semibold text-white/70"
				>
					{initials(name)}
				</span>
				{src && (
					<FadeImage
						key={src}
						src={src}
						className="absolute inset-0 size-full object-cover"
					/>
				)}
			</div>
			<p className="mt-2 truncate text-sm font-medium">{name}</p>
			{role && <p className="truncate text-xs text-ink-muted">{role}</p>}
		</div>
	);
}

/** The width of a cast member's slot in a shelf. */
export const personSlot = "w-28 shrink-0 sm:w-32";

function PersonCard({ person }: { person: BaseItemPerson }) {
	const src =
		person.Id && person.PrimaryImageTag
			? imageUrl(person.Id, "Primary", {
					tag: person.PrimaryImageTag,
					maxWidth: 300,
				})
			: undefined;
	return (
		<PersonView
			name={person.Name ?? ""}
			role={person.Role ?? undefined}
			src={src}
		/>
	);
}

/** A row of cast members with their photos. */
export function PeopleShelf({
	title,
	people,
}: {
	title: string;
	people: BaseItemPerson[];
}) {
	if (people.length === 0) return null;
	return (
		<Shelf title={title}>
			{people.map((person) => (
				<li key={`${person.Id}:${person.Role}`} className={personSlot}>
					<PersonCard person={person} />
				</li>
			))}
		</Shelf>
	);
}
